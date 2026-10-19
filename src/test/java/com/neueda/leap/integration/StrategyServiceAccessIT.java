package com.neueda.leap.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.marketdata.PriceQuote;
import com.neueda.leap.marketdata.PriceUpdatedEvent;
import com.neueda.leap.model.Account;
import com.neueda.leap.model.Instrument;
import com.neueda.leap.model.Order;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.InstrumentRepository;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.time.Clock;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The strategy service's token (role STRATEGY, docs/DECISIONS.md #16) may
 * place and read orders only for an account whose owner has an ACTIVE
 * strategy, and nothing else; and live prices reach the market-data topic it
 * consumes.
 */
@DisplayName("Strategy service access")
@WithMockUser(username = "service:strategy-service", roles = "STRATEGY")
class StrategyServiceAccessIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private InstrumentRepository instrumentRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private Clock clock;
    @Autowired
    private ApplicationEventPublisher events;
    @Autowired
    private ObjectMapper objectMapper;

    private Account withStrategy;
    private Account withPausedStrategy;

    @BeforeEach
    void setUp() {
        withStrategy = accountRepository.save(new Account("ACC-S000001", "Strategist", new BigDecimal("10000.00"), clock));
        withPausedStrategy = accountRepository.save(new Account("ACC-S000002", "Paused", new BigDecimal("10000.00"), clock));
        instrumentRepository.save(new Instrument("SPY", "SPDR S&P 500 ETF", "ETF", "USD", true));
        addStrategy(withStrategy, "ACTIVE");
        addStrategy(withPausedStrategy, "PAUSED");
    }

    private void addStrategy(Account account, String status) {
        jdbcTemplate.update("""
                INSERT INTO strategies (account_id, name, symbol, rule_type, rule_params, quantity, status)
                VALUES (?, 'MA 20/50', 'SPY', 'MA_CROSSOVER', '{"shortWindow":20,"longWindow":50}'::jsonb, 1, ?)""",
                account.getId(), status);
    }

    private static String marketBuy(Long accountId) {
        // With a price: the tests run without live prices, so the API cannot price a market order
        return """
                {"accountId": %d, "symbol": "SPY", "side": "BUY", "quantity": 1, "price": 580.00,
                 "idempotencyKey": "stg-%s"}""".formatted(accountId, UUID.randomUUID());
    }

    @Test
    @DisplayName("places and reads orders for an account with an ACTIVE strategy")
    void tradesForActiveStrategy() throws Exception {
        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                .content(marketBuy(withStrategy.getId())))
                .andExpect(status().isAccepted());

        Order order = orderRepository.findAll().get(0);
        mockMvc.perform(get("/api/v1/orders/{id}", order.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(withStrategy.getId().intValue()));
    }

    @Test
    @DisplayName("is refused an account whose strategy is paused, or that has none")
    void refusedWithoutActiveStrategy() throws Exception {
        Order paused = orderRepository.save(new Order(withPausedStrategy.getId(), "SPY", OrderSide.BUY, 1,
                new BigDecimal("580.00"), UUID.randomUUID().toString(), clock));

        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                .content(marketBuy(withPausedStrategy.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("AUTH-403"));
        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                .content(marketBuy(999_999L)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/orders/{id}", paused.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("cannot use any other endpoint, even on an account with an ACTIVE strategy")
    void refusedEverythingElse() throws Exception {
        Long id = withStrategy.getId();
        mockMvc.perform(get("/api/v1/accounts/{id}", id)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/accounts/{id}/withdraw", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": 1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("AUTH-403"));
        mockMvc.perform(get("/api/v1/orders")).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/orders/{id}", UUID.randomUUID())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/positions/{id}", id)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/market-data/replays").contentType(MediaType.APPLICATION_JSON)
                .content("{\"symbol\": \"SPY\", \"date\": \"2026-10-09\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/dlq/messages")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "trader", roles = "USER")
    @DisplayName("a user cannot start a replay without market data keys (MKT-503)")
    void replayNeedsMarketDataKeys() throws Exception {
        mockMvc.perform(post("/api/v1/market-data/replays").contentType(MediaType.APPLICATION_JSON)
                .content("{\"symbol\": \"SPY\", \"date\": \"2026-10-09\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("MKT-503"));
        mockMvc.perform(post("/api/v1/market-data/replays").contentType(MediaType.APPLICATION_JSON)
                .content("{\"symbol\": \"SPY\", \"date\": \"2999-01-01\", \"barsPerSecond\": 500}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VAL-422"));
        mockMvc.perform(delete("/api/v1/market-data/replays/current"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MKT-404"));
    }

    @Test
    @DisplayName("live prices are published on the market-data topic, keyed by symbol")
    void livePricesReachTheMarketDataTopic() throws Exception {
        Instant tradedAt = Instant.parse("2026-10-19T14:30:00Z");
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "market-data-it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.subscribe(List.of("market-data"));
            // A symbol no other test publishes, so the sampling interval cannot hold it back
            events.publishEvent(new PriceUpdatedEvent(new PriceQuote("ITMD", new BigDecimal("123.45"), tradedAt)));

            ConsumerRecord<String, String> received = null;
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (received == null && System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> consumed : consumer.poll(Duration.ofMillis(500))) {
                    if ("ITMD".equals(consumed.key())) {
                        received = consumed;
                    }
                }
            }

            assertNotNull(received, "no ITMD price on market-data");
            JsonNode payload = objectMapper.readTree(received.value()).get("payload");
            assertEquals("LIVE", payload.get("source").asText());
            assertEquals(new BigDecimal("123.45"), payload.get("price").decimalValue());
            assertEquals(tradedAt, Instant.parse(payload.get("time").asText()));
        }
    }
}
