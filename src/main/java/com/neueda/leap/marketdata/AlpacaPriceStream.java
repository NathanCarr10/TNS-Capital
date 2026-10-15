package com.neueda.leap.marketdata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.model.Instrument;
import com.neueda.leap.repositories.InstrumentRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Streams live trades from Alpaca over a WebSocket into the live price pool.
 *
 * Protocol: after connecting, Alpaca sends "connected"; we reply with the API
 * keys, it sends "authenticated", and we subscribe to trades for the tradable
 * instruments. From then on every trade arrives as {"T":"t","S":symbol,"p":price,...}.
 *
 * If the connection drops it reconnects, waiting 1s, 2s, 4s... up to a minute.
 * It does not reconnect after errors that a retry cannot fix (wrong keys, or
 * a feed the plan does not include).
 *
 * The free plan allows one stream connection per account, so only one
 * instance of the app can stream at a time; another instance gets error 406
 * and keeps retrying, pricing orders over REST in the meantime.
 */
@Component
@Slf4j
public class AlpacaPriceStream {
    private static final Duration MIN_RECONNECT_DELAY = Duration.ofSeconds(1);
    private static final Duration MAX_RECONNECT_DELAY = Duration.ofMinutes(1);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    // 402 auth failed, 409 insufficient subscription (e.g. "sip" on the free plan)
    private static final Set<Integer> FATAL_ERROR_CODES = Set.of(402, 409);

    private final AlpacaProperties properties;
    private final LivePriceService prices;
    private final InstrumentRepository instrumentRepository;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "alpaca-price-stream");
        thread.setDaemon(true);
        return thread;
    });

    private volatile boolean running;
    private final AtomicReference<WebSocket> webSocket = new AtomicReference<>();
    private Duration reconnectDelay = MIN_RECONNECT_DELAY;

    public AlpacaPriceStream(
            AlpacaProperties properties,
            LivePriceService prices,
            InstrumentRepository instrumentRepository,
            ObjectMapper objectMapper) {
        this.properties = Objects.requireNonNull(properties);
        this.prices = Objects.requireNonNull(prices);
        this.instrumentRepository = Objects.requireNonNull(instrumentRepository);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!properties.isConfigured()) {
            log.warn("ALPACA_API_KEY_ID / ALPACA_API_SECRET_KEY are not set: live prices are off, "
                    + "so orders must include a price");
            return;
        }
        running = true;
        // Off the startup thread: fill the pool over REST first, so orders can be
        // priced straight away, then open the stream
        scheduler.execute(() -> {
            prices.refresh(streamedSymbols());
            connect();
        });
    }

    @PreDestroy
    public void stop() {
        running = false;
        scheduler.shutdownNow();
        WebSocket current = webSocket.get();
        if (current != null) {
            current.abort();
        }
    }

    /**
     * The tradable instruments, up to the plan's symbol limit. Read on every
     * (re)connect, so instruments added since startup are picked up then.
     */
    List<String> streamedSymbols() {
        List<String> tradable = instrumentRepository.findAll().stream()
                .filter(Instrument::isTradable)
                .map(Instrument::getSymbol)
                .sorted()
                .toList();
        if (tradable.size() > properties.maxSymbols()) {
            log.warn("{} tradable instruments but the stream allows {}; {} are priced over REST only",
                    tradable.size(), properties.maxSymbols(), tradable.subList(properties.maxSymbols(), tradable.size()));
            return tradable.subList(0, properties.maxSymbols());
        }
        return tradable;
    }

    private void connect() {
        if (!running) {
            return;
        }
        log.info("Connecting to Alpaca price stream {}", properties.streamUri());
        HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .buildAsync(properties.streamUri(), new Listener())
                .whenComplete((connected, error) -> {
                    if (error != null) {
                        log.warn("Could not connect to Alpaca price stream: {}", error.getMessage());
                        scheduleReconnect();
                    } else {
                        webSocket.set(connected);
                    }
                });
    }

    private synchronized void scheduleReconnect() {
        if (!running) {
            return;
        }
        Duration delay = reconnectDelay;
        reconnectDelay = delay.multipliedBy(2).compareTo(MAX_RECONNECT_DELAY) > 0
                ? MAX_RECONNECT_DELAY
                : delay.multipliedBy(2);
        log.info("Reconnecting to Alpaca price stream in {}s", delay.toSeconds());
        try {
            scheduler.schedule(this::connect, delay.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            // Shutting down
        }
    }

    private synchronized void resetReconnectDelay() {
        reconnectDelay = MIN_RECONNECT_DELAY;
    }

    /**
     * Handles one message from the stream. Alpaca sends each message as a JSON
     * array of events.
     *
     * @param payload the message text
     * @param send    sends a reply on the same connection
     */
    void handleMessage(String payload, Consumer<String> send) {
        JsonNode events;
        try {
            events = objectMapper.readTree(payload);
        } catch (JsonProcessingException e) {
            log.warn("Ignoring unreadable Alpaca message: {}", e.getMessage());
            return;
        }
        for (JsonNode event : events) {
            switch (event.path("T").asText()) {
                case "t" -> onTrade(event);
                case "success" -> onSuccess(event.path("msg").asText(), send);
                case "subscription" -> {
                    log.info("Streaming Alpaca trades for {}", event.path("trades"));
                    resetReconnectDelay();
                }
                case "error" -> onError(event.path("code").asInt(), event.path("msg").asText());
                default -> {
                    // Other event types (quotes, bars...) are not subscribed to
                }
            }
        }
    }

    private void onSuccess(String message, Consumer<String> send) {
        if ("connected".equals(message)) {
            send.accept(toJson(Map.of(
                    "action", "auth",
                    "key", properties.keyId(),
                    "secret", properties.secretKey())));
        } else if ("authenticated".equals(message)) {
            send.accept(toJson(Map.of(
                    "action", "subscribe",
                    "trades", streamedSymbols())));
        }
    }

    private void onTrade(JsonNode trade) {
        String symbol = trade.path("S").asText(null);
        JsonNode price = trade.get("p");
        String tradedAt = trade.path("t").asText(null);
        if (symbol == null || price == null || !price.isNumber() || tradedAt == null) {
            log.debug("Ignoring incomplete trade message: {}", trade);
            return;
        }
        try {
            BigDecimal value = price.decimalValue();
            if (value.signum() > 0) {
                prices.update(new PriceQuote(symbol, value, Instant.parse(tradedAt)));
            }
        } catch (DateTimeParseException e) {
            log.debug("Ignoring trade with unreadable timestamp: {}", trade);
        }
    }

    private void onError(int code, String message) {
        if (FATAL_ERROR_CODES.contains(code)) {
            running = false;
            log.error("Alpaca price stream refused the connection ({} {}); live prices are off. "
                    + "Check ALPACA_API_KEY_ID, ALPACA_API_SECRET_KEY and ALPACA_FEED", code, message);
        } else {
            log.warn("Alpaca price stream error {}: {}", code, message);
        }
    }

    private String toJson(Map<String, Object> message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not write Alpaca message", e);
        }
    }

    private final class Listener implements WebSocket.Listener {
        // A message can arrive split over several frames
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String payload = buffer.toString();
                buffer.setLength(0);
                handleMessage(payload, reply -> socket.sendText(reply, true));
            }
            socket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket socket, int statusCode, String reason) {
            log.warn("Alpaca price stream closed ({} {})", statusCode, reason);
            scheduleReconnect();
            return null;
        }

        @Override
        public void onError(WebSocket socket, Throwable error) {
            log.warn("Alpaca price stream failed: {}", error.getMessage());
            scheduleReconnect();
        }
    }
}
