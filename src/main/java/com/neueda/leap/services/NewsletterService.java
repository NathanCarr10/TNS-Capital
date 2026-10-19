package com.neueda.leap.services;

import com.neueda.leap.config.NewsletterProperties;
import com.neueda.leap.exceptions.MarketDataUnavailableException;
import com.neueda.leap.exceptions.NewsletterNotFoundException;
import com.neueda.leap.marketdata.AlpacaRestClient;
import com.neueda.leap.marketdata.BarTimeframe;
import com.neueda.leap.marketdata.MarketDataService;
import com.neueda.leap.marketdata.NewsArticle;
import com.neueda.leap.marketdata.PriceBar;
import com.neueda.leap.model.Instrument;
import com.neueda.leap.model.NewsletterIssue;
import com.neueda.leap.model.NewsletterMover;
import com.neueda.leap.model.NewsletterNewsItem;
import com.neueda.leap.repositories.InstrumentRepository;
import com.neueda.leap.repositories.NewsletterIssueRepository;
import com.neueda.leap.time.Clock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Publishes the weekly market newsletter: the tradable instruments that rose
 * and fell the most this week, with the latest news about each of them.
 *
 * A week's change runs from the close of the last trading day before its
 * Monday to the latest close since, so an issue published mid-week covers the
 * week so far. There is one issue per week; publishing again returns it.
 * News is optional: without it the issue is still published, without
 * headlines. Without prices there is nothing to rank and nothing is published.
 */
@Service
@Slf4j
public class NewsletterService {
    // Two weeks of daily bars hold the close before Monday even after a holiday
    static final int DAILY_BARS = 10;
    // Alpaca's largest news page
    static final int NEWS_LIMIT = 50;

    private final NewsletterIssueRepository issueRepository;
    private final InstrumentRepository instrumentRepository;
    private final MarketDataService marketDataService;
    private final AlpacaRestClient alpacaRestClient;
    private final NewsletterProperties properties;
    private final Clock clock;

    public NewsletterService(NewsletterIssueRepository issueRepository, InstrumentRepository instrumentRepository,
            MarketDataService marketDataService, AlpacaRestClient alpacaRestClient, NewsletterProperties properties,
            Clock clock) {
        this.issueRepository = Objects.requireNonNull(issueRepository);
        this.instrumentRepository = Objects.requireNonNull(instrumentRepository);
        this.marketDataService = Objects.requireNonNull(marketDataService);
        this.alpacaRestClient = Objects.requireNonNull(alpacaRestClient);
        this.properties = Objects.requireNonNull(properties);
        this.clock = Objects.requireNonNull(clock);
    }

    /** The weekly job; see newsletter.cron in application.yml. */
    @Scheduled(cron = "${newsletter.cron}", zone = "${newsletter.zone}")
    public void publishScheduled() {
        try {
            publishWeeklyIssue();
        } catch (MarketDataUnavailableException e) {
            log.error("Weekly newsletter not published: {}", e.getMessage());
        }
    }

    /**
     * Publishes this week's issue, or returns it if it was published already.
     *
     * @throws MarketDataUnavailableException if no instrument has prices for the week
     */
    public NewsletterIssue publishWeeklyIssue() {
        LocalDate weekStart = clock.now().atZone(properties.zone()).toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        Optional<NewsletterIssue> published = issueRepository.findByWeekStart(weekStart);
        if (published.isPresent()) {
            log.info("Newsletter for the week of {} was already published", weekStart);
            return published.get();
        }

        List<NewsletterMover> movers = weeklyMovers(weekStart);
        if (movers.isEmpty()) {
            throw new MarketDataUnavailableException("No prices to rank the movers of the week of " + weekStart);
        }
        List<NewsletterMover> gainers = movers.stream()
                .filter(mover -> mover.changePct().signum() > 0)
                .sorted(Comparator.comparing(NewsletterMover::changePct).reversed()
                        .thenComparing(NewsletterMover::symbol))
                .limit(properties.moversPerSide())
                .toList();
        List<NewsletterMover> losers = movers.stream()
                .filter(mover -> mover.changePct().signum() < 0)
                .sorted(Comparator.comparing(NewsletterMover::changePct)
                        .thenComparing(NewsletterMover::symbol))
                .limit(properties.moversPerSide())
                .toList();
        List<NewsletterNewsItem> news = newsAbout(Stream.concat(gainers.stream(), losers.stream()).toList(),
                weekStart.atStartOfDay(properties.zone()).toInstant());

        try {
            NewsletterIssue issue = issueRepository.save(
                    new NewsletterIssue(weekStart, clock.now(), gainers, losers, news));
            log.info("Published the newsletter for the week of {}: {} gainers, {} losers, {} headlines",
                    weekStart, gainers.size(), losers.size(), news.size());
            return issue;
        } catch (DataIntegrityViolationException e) {
            // Published by another run in the meantime (week_start is unique)
            return issueRepository.findByWeekStart(weekStart).orElseThrow(() -> e);
        }
    }

    /**
     * @return every published issue, newest first
     */
    @Transactional(readOnly = true)
    public List<NewsletterIssue> issues() {
        return issueRepository.findAllByOrderByWeekStartDesc();
    }

    /**
     * @throws NewsletterNotFoundException if there is no issue with that ID
     */
    @Transactional(readOnly = true)
    public NewsletterIssue issue(long id) {
        return issueRepository.findById(id)
                .orElseThrow(() -> new NewsletterNotFoundException("Newsletter issue not found: " + id));
    }

    /**
     * @return the week's change of every tradable instrument with prices from
     *         both before and during the week, in no particular order
     */
    private List<NewsletterMover> weeklyMovers(LocalDate weekStart) {
        List<NewsletterMover> movers = new ArrayList<>();
        for (Instrument instrument : instrumentRepository.findAll()) {
            if (instrument.isTradable()) {
                weeklyChange(instrument, weekStart).ifPresent(movers::add);
            }
        }
        return movers;
    }

    private Optional<NewsletterMover> weeklyChange(Instrument instrument, LocalDate weekStart) {
        BigDecimal startClose = null;
        BigDecimal endClose = null;
        // Bars come oldest first, so the last of each side wins
        for (PriceBar bar : marketDataService.bars(instrument.getSymbol(), BarTimeframe.DAY_1, DAILY_BARS)) {
            LocalDate tradingDay = bar.time().atZone(properties.zone()).toLocalDate();
            if (tradingDay.isBefore(weekStart)) {
                startClose = bar.close();
            } else {
                endClose = bar.close();
            }
        }
        if (startClose == null || endClose == null || startClose.signum() <= 0) {
            log.debug("No weekly change for {}: closes before and during the week are needed",
                    instrument.getSymbol());
            return Optional.empty();
        }
        BigDecimal changePct = endClose.subtract(startClose)
                .multiply(BigDecimal.valueOf(100))
                .divide(startClose, 2, RoundingMode.HALF_UP);
        return Optional.of(new NewsletterMover(instrument.getSymbol(), instrument.getName(), startClose, endClose,
                changePct));
    }

    /**
     * Up to headlinesPerMover of the newest articles about each mover, gainers
     * first. An article about several movers is shown under the first only.
     */
    private List<NewsletterNewsItem> newsAbout(List<NewsletterMover> movers, Instant since) {
        if (movers.isEmpty()) {
            return List.of();
        }
        List<NewsArticle> articles = alpacaRestClient.news(
                movers.stream().map(NewsletterMover::symbol).toList(), since, NEWS_LIMIT).stream()
                .sorted(Comparator.comparing(NewsArticle::publishedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        if (articles.isEmpty()) {
            log.warn("No news found for the newsletter's movers; publishing it without headlines");
            return List.of();
        }

        Set<String> shown = new HashSet<>();
        List<NewsletterNewsItem> news = new ArrayList<>();
        for (NewsletterMover mover : movers) {
            articles.stream()
                    .filter(article -> article.symbols().contains(mover.symbol()))
                    .filter(article -> !shown.contains(article.url()))
                    .limit(properties.headlinesPerMover())
                    .forEach(article -> {
                        shown.add(article.url());
                        news.add(new NewsletterNewsItem(mover.symbol(), article.headline(), article.source(),
                                article.url(), article.publishedAt()));
                    });
        }
        return news;
    }
}
