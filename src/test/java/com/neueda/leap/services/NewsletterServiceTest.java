package com.neueda.leap.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.neueda.leap.config.NewsletterProperties;
import com.neueda.leap.exceptions.MarketDataUnavailableException;
import com.neueda.leap.exceptions.NewsletterPublishingDisabledException;
import com.neueda.leap.marketdata.BarTimeframe;
import com.neueda.leap.marketdata.MarketDataService;
import com.neueda.leap.marketdata.PriceBar;
import com.neueda.leap.model.Instrument;
import com.neueda.leap.model.NewsletterIssue;
import com.neueda.leap.model.NewsletterMover;
import com.neueda.leap.repositories.InstrumentRepository;
import com.neueda.leap.repositories.NewsletterIssueRepository;
import com.neueda.leap.time.FixedClock;

@DisplayName("NewsletterService")
class NewsletterServiceTest {
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    // Friday 2026-10-16, 17:00 in New York: the scheduled run
    private static final Instant NOW = Instant.parse("2026-10-16T21:00:00Z");
    private static final LocalDate WEEK_START = LocalDate.of(2026, 10, 12);

    private final NewsletterIssueRepository issueRepository = mock(NewsletterIssueRepository.class);
    private final InstrumentRepository instrumentRepository = mock(InstrumentRepository.class);
    private final MarketDataService marketDataService = mock(MarketDataService.class);
    private final FixedClock clock = new FixedClock(NOW);
    private final List<Instrument> instruments = new ArrayList<>();

    private NewsletterService service(int moversPerSide) {
        return service(moversPerSide, false);
    }

    private NewsletterService service(int moversPerSide, boolean earlyPublish) {
        return new NewsletterService(issueRepository, instrumentRepository, marketDataService,
                new NewsletterProperties("-", NEW_YORK, moversPerSide, earlyPublish), clock);
    }

    private final NewsletterService service = service(5);

    @BeforeEach
    void setUp() {
        when(instrumentRepository.findAll()).thenReturn(instruments);
        when(issueRepository.findByWeekStart(any())).thenReturn(Optional.empty());
        when(issueRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    /** A daily bar for the trading day, timed at midnight New York as Alpaca does */
    private static PriceBar bar(String day, String close) {
        Instant time = LocalDate.parse(day).atStartOfDay(NEW_YORK).toInstant();
        BigDecimal price = new BigDecimal(close);
        return new PriceBar(time, price, price, price, price, 1000);
    }

    /** A tradable instrument whose closes were start on Friday 9 Oct and end on Friday 16 Oct */
    private void instrument(String symbol, String start, String end) {
        instruments.add(new Instrument(symbol, symbol + " Inc", "EQUITY", "USD", true));
        when(marketDataService.bars(symbol, BarTimeframe.DAY_1, NewsletterService.DAILY_BARS)).thenReturn(List.of(
                bar("2026-10-08", "1.00"), bar("2026-10-09", start),
                bar("2026-10-12", "1.00"), bar("2026-10-16", end)));
    }

    private static List<String> symbols(List<NewsletterMover> movers) {
        return movers.stream().map(NewsletterMover::symbol).toList();
    }

    @Test
    @DisplayName("Measures each change from the close before Monday to the latest close")
    void measuresWeeklyChange() {
        instrument("AAPL", "200.00", "210.00");

        NewsletterIssue issue = service.publishWeeklyIssue();

        assertEquals(WEEK_START, issue.getWeekStart());
        assertEquals(NOW, issue.getGeneratedAt());
        assertEquals(List.of(new NewsletterMover("AAPL", "AAPL Inc", new BigDecimal("200.00"),
                new BigDecimal("210.00"), new BigDecimal("5.00"))), issue.getGainers());
        verify(issueRepository).save(issue);
    }

    @Test
    @DisplayName("Lists gainers best first and losers worst first, leaving out unchanged instruments")
    void ranksMovers() {
        instrument("AAA", "100", "103");
        instrument("BBB", "100", "110");
        instrument("CCC", "100", "100");
        instrument("DDD", "100", "95");
        instrument("EEE", "100", "80");

        NewsletterIssue issue = service.publishWeeklyIssue();

        assertEquals(List.of("BBB", "AAA"), symbols(issue.getGainers()));
        assertEquals(List.of("EEE", "DDD"), symbols(issue.getLosers()));
        assertEquals(new BigDecimal("-20.00"), issue.getLosers().get(0).changePct());
    }

    @Test
    @DisplayName("Keeps only the configured number of movers on each side")
    void limitsMovers() {
        instrument("AAA", "100", "101");
        instrument("BBB", "100", "102");
        instrument("CCC", "100", "103");
        instrument("DDD", "100", "99");
        instrument("EEE", "100", "98");
        instrument("FFF", "100", "97");

        NewsletterIssue issue = service(2).publishWeeklyIssue();

        assertEquals(List.of("CCC", "BBB"), symbols(issue.getGainers()));
        assertEquals(List.of("FFF", "EEE"), symbols(issue.getLosers()));
    }

    @Test
    @DisplayName("Rounds the change to two decimal places")
    void roundsChange() {
        instrument("AAPL", "3.00", "4.00");

        assertEquals(new BigDecimal("33.33"), service.publishWeeklyIssue().getGainers().get(0).changePct());
    }

    @Test
    @DisplayName("Leaves out instruments that are not tradable or lack closes before or during the week")
    void skipsInstrumentsWithoutWeeklyChange() {
        instrument("AAPL", "100", "110");
        instruments.add(new Instrument("OLD", "Old Corp", "EQUITY", "USD", false));
        instruments.add(new Instrument("NEW", "Listed this week", "EQUITY", "USD", true));
        when(marketDataService.bars("NEW", BarTimeframe.DAY_1, NewsletterService.DAILY_BARS))
                .thenReturn(List.of(bar("2026-10-14", "10"), bar("2026-10-16", "20")));
        instruments.add(new Instrument("HALT", "Halted all week", "EQUITY", "USD", true));
        when(marketDataService.bars("HALT", BarTimeframe.DAY_1, NewsletterService.DAILY_BARS))
                .thenReturn(List.of(bar("2026-10-08", "10"), bar("2026-10-09", "5")));

        NewsletterIssue issue = service.publishWeeklyIssue();

        assertEquals(List.of("AAPL"), symbols(issue.getGainers()));
        assertTrue(issue.getLosers().isEmpty());
        verify(marketDataService, never()).bars(eq("OLD"), any(), anyInt());
    }

    @Test
    @DisplayName("A mid-week run covers the week so far")
    void coversWeekSoFar() {
        clock.setNow(Instant.parse("2026-10-14T15:00:00Z"));
        instrument("AAPL", "100", "90");

        NewsletterIssue issue = service.publishWeeklyIssue();

        assertEquals(WEEK_START, issue.getWeekStart());
        assertEquals(List.of("AAPL"), symbols(issue.getLosers()));
    }

    @Test
    @DisplayName("Returns the week's issue without calling Alpaca when it was published already")
    void returnsPublishedIssue() {
        NewsletterIssue published = new NewsletterIssue(WEEK_START, NOW, List.of(), List.of());
        when(issueRepository.findByWeekStart(WEEK_START)).thenReturn(Optional.of(published));

        assertSame(published, service.publishWeeklyIssue());

        verifyNoInteractions(marketDataService);
        verify(issueRepository, never()).save(any());
    }

    @Test
    @DisplayName("Returns the other run's issue when one is saved for the week at the same time")
    void returnsIssueSavedConcurrently() {
        instrument("AAPL", "100", "110");
        NewsletterIssue other = new NewsletterIssue(WEEK_START, NOW, List.of(), List.of());
        when(issueRepository.findByWeekStart(WEEK_START)).thenReturn(Optional.empty()).thenReturn(Optional.of(other));
        when(issueRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate week_start"));

        assertSame(other, service.publishWeeklyIssue());
    }

    @Test
    @DisplayName("Publishes nothing when there are no prices to rank")
    void publishesNothingWithoutPrices() {
        instruments.add(new Instrument("AAPL", "Apple", "EQUITY", "USD", true));
        when(marketDataService.bars(any(), any(), anyInt())).thenReturn(List.of());

        assertThrows(MarketDataUnavailableException.class, service::publishWeeklyIssue);

        verify(issueRepository, never()).save(any());
    }

    @Test
    @DisplayName("The scheduled run logs a failure instead of throwing it")
    void scheduledRunSurvivesMissingPrices() {
        assertDoesNotThrow(service::publishScheduled);

        verify(issueRepository, never()).save(any());
    }

    @Test
    @DisplayName("Previews the week so far without saving it")
    void previewsWithoutSaving() {
        clock.setNow(Instant.parse("2026-10-14T15:00:00Z"));
        instrument("AAPL", "100", "110");

        NewsletterIssue preview = service.previewWeeklyIssue();

        assertNull(preview.getId());
        assertEquals(WEEK_START, preview.getWeekStart());
        assertEquals(List.of("AAPL"), symbols(preview.getGainers()));
        verify(issueRepository, never()).save(any());
    }

    @Test
    @DisplayName("Previews from the latest prices even when the week's issue was published")
    void previewIgnoresPublishedIssue() {
        when(issueRepository.findByWeekStart(WEEK_START)).thenReturn(Optional.of(
                new NewsletterIssue(WEEK_START, NOW, List.of(), List.of())));
        instrument("AAPL", "100", "90");

        NewsletterIssue preview = service.previewWeeklyIssue();

        assertEquals(List.of("AAPL"), symbols(preview.getLosers()));
        verify(issueRepository, never()).save(any());
    }

    @Test
    @DisplayName("A preview without prices fails like publishing does")
    void previewWithoutPrices() {
        assertThrows(MarketDataUnavailableException.class, service::previewWeeklyIssue);
    }

    @Test
    @DisplayName("Refuses to publish early unless early publishing is switched on")
    void earlyPublishingOffByDefault() {
        instrument("AAPL", "100", "110");

        assertThrows(NewsletterPublishingDisabledException.class, service::publishEarly);

        verifyNoInteractions(marketDataService);
        verify(issueRepository, never()).save(any());
    }

    @Test
    @DisplayName("Publishes the week so far early when switched on")
    void publishesEarly() {
        clock.setNow(Instant.parse("2026-10-14T15:00:00Z"));
        instrument("AAPL", "100", "110");

        NewsletterIssue issue = service(5, true).publishEarly();

        assertEquals(WEEK_START, issue.getWeekStart());
        assertEquals(List.of("AAPL"), symbols(issue.getGainers()));
        verify(issueRepository).save(issue);
    }

    @Test
    @DisplayName("Returns the week's issue when publishing early after it was published")
    void publishEarlyReturnsPublishedIssue() {
        NewsletterIssue published = new NewsletterIssue(WEEK_START, NOW, List.of(), List.of());
        when(issueRepository.findByWeekStart(WEEK_START)).thenReturn(Optional.of(published));

        assertSame(published, service(5, true).publishEarly());

        verify(issueRepository, never()).save(any());
    }
}
