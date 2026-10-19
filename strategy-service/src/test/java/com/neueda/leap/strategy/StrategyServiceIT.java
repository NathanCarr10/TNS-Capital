package com.neueda.leap.strategy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.strategy.domain.StrategySignal;
import com.neueda.leap.strategy.domain.Position;
import com.neueda.leap.strategy.domain.StrategyRepository;
import com.neueda.leap.strategy.domain.StrategySignalRepository;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The strategy service against the real database schema (db/, connected as
 * its least-privilege role tns_strategy) and a real Kafka, with the auth
 * service and trading API stood in for by a local HTTP server: create a
 * strategy, backtest it, switch it on, and watch a replayed crossover place
 * an order with the service's own token.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Strategy service")
class StrategyServiceIT {
    static final String SECRET = "integration-test-secret-at-least-32-bytes";
    private static final Path CONTRACT = Path.of("../contracts/strategy-api.json");
    private static final Path LIVE_COPY = Path.of("target/strategy-api.json");
    private static final String ISSUER = "urn:tns-capital:auth-service";

    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("tns_capital")
            .withEnv("DB_USER", "test")
            .withUsername("test")
            .withPassword("test")
            // Same layout as db/Dockerfile: tables, views, the services' roles, then data
            .withCopyFileToContainer(MountableFile.forHostPath("../db/docker-entrypoint-initdb.sh", 0755),
                    "/docker-entrypoint-initdb.d/00-run-sql.sh")
            .withCopyFileToContainer(MountableFile.forHostPath("../db/tables"), "/docker-entrypoint-initdb.d/tables")
            .withCopyFileToContainer(MountableFile.forHostPath("../db/views"), "/docker-entrypoint-initdb.d/views")
            .withCopyFileToContainer(MountableFile.forHostPath("../db/roles"), "/docker-entrypoint-initdb.d/roles")
            .withCopyFileToContainer(MountableFile.forHostPath("../db/data"), "/docker-entrypoint-initdb.d/data");

    @SuppressWarnings("resource")
    static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    /** Stands in for the auth service (POST /auth/token) and the trading API (POST /api/v1/orders) */
    static final HttpServer platform;
    static final List<Map.Entry<String, String>> ordersReceived = new CopyOnWriteArrayList<>();

    static {
        postgres.start();
        kafka.start();
        try {
            platform = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        platform.createContext("/auth/token", exchange -> reply(exchange, 200,
                "{\"accessToken\": \"service-token\", \"tokenType\": \"Bearer\", \"expiresIn\": 900}"));
        platform.createContext("/api/v1/orders", exchange -> {
            ordersReceived.add(Map.entry(exchange.getRequestHeaders().getFirst("Authorization"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            reply(exchange, 202, "{\"orderId\": \"" + UUID.randomUUID() + "\", \"status\": \"NEW\"}");
        });
        platform.start();
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        // The role the service runs as; its password defaults to the owner's in db/roles
        registry.add("spring.datasource.username", () -> "tns_strategy");
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        // The test publishes as soon as the context is up, possibly before the partitions are assigned
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("jwt.shared-secret", () -> SECRET);
        String platformUrl = "http://localhost:" + platform.getAddress().getPort();
        registry.add("strategy.trading-api-url", () -> platformUrl);
        registry.add("strategy.auth-url", () -> platformUrl);
        registry.add("strategy.client-secret", () -> "client-secret");
        // The stand-in API stores no orders, so they never settle: do not wait long for them
        registry.add("strategy.order-settlement.timeout", () -> "300ms");
    }

    @AfterAll
    static void stopPlatform() {
        platform.stop(0);
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        try (InputStream ignored = exchange.getRequestBody()) {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private StrategySignalRepository signals;
    @Autowired
    private StrategyRepository strategies;

    private final JdbcTemplate owner = new JdbcTemplate(
            new org.springframework.jdbc.datasource.DriverManagerDataSource(postgres.getJdbcUrl(), "test", "test"));
    private long traderAccount;

    @BeforeEach
    void setUp() throws SQLException {
        owner.execute("TRUNCATE accounts, instruments RESTART IDENTITY CASCADE");
        ordersReceived.clear();
        owner.update("INSERT INTO instruments (symbol, name, asset_class, currency, tradable) VALUES "
                + "('SPY', 'SPDR S&P 500 ETF', 'ETF', 'USD', true), ('OLD', 'Delisted', 'EQUITY', 'USD', false)");
        traderAccount = linkUser("trader", "ACC-T0001");
        linkUser("someone", "ACC-T0002");
        loadDailyPrices();
    }

    private long linkUser(String username, String accountNumber) {
        Long accountId = owner.queryForObject(
                "INSERT INTO accounts (account_number, holder_name, cash_balance) VALUES (?, ?, 10000) RETURNING id",
                Long.class, accountNumber, username);
        owner.update("INSERT INTO users (username, password_hash, role, account_id) VALUES (?, 'x', 'USER', ?)",
                username, accountId);
        return accountId;
    }

    /** As the ETL does: its role creates the analytics table and loads 300 days of SPY closes */
    private static void loadDailyPrices() throws SQLException {
        try (Connection etl = DriverManager.getConnection(postgres.getJdbcUrl(), "tns_etl", postgres.getPassword());
                Statement statement = etl.createStatement()) {
            statement.execute(etlTable());
            statement.execute("TRUNCATE analytics.fact_daily_prices");
            statement.execute("""
                    INSERT INTO analytics.fact_daily_prices (symbol, trade_date, open, high, low, close, volume)
                    SELECT 'SPY', DATE '2025-01-01' + n, 1, 1, 1, 400 + 40 * sin(n / 15.0), 1000
                    FROM generate_series(0, 299) AS n""");
        }
    }

    private static String etlTable() {
        try {
            String schema = Files.readString(Path.of("../etl/schema.sql"));
            int start = schema.indexOf("CREATE TABLE IF NOT EXISTS analytics.fact_daily_prices");
            return schema.substring(start, schema.indexOf(");", start) + 2);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String token(String username, String... roles) throws JOSEException {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(username)
                .issuer(ISSUER)
                .claim("typ", "access")
                .claim("roles", List.of(roles))
                .jwtID(UUID.randomUUID().toString())
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(900)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(SECRET));
        return "Bearer " + jwt.serialize();
    }

    private long createStrategy(String auth) throws Exception {
        String created = mockMvc.perform(post("/api/v1/strategies").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Fast MA", "symbol": "SPY", "ruleType": "MA_CROSSOVER",
                         "params": {"shortWindow": 2, "longWindow": 4}, "quantity": 3}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PAUSED"))
                .andExpect(jsonPath("$.accountId").value(traderAccount))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("id").asLong();
    }

    private static void publishReplay(String replayId, double... closes) {
        Instant open = Instant.parse("2026-10-09T13:30:00Z");
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            for (int minute = 0; minute < closes.length; minute++) {
                producer.send(new ProducerRecord<>("market-data", "SPY", """
                        {"timestamp": "%s", "correlationId": "%s", "schemaVersion": "1.0", "messageType": "MarketTick",
                         "payload": {"symbol": "SPY", "price": %s, "time": "%s", "source": "REPLAY", "replayId": "%s"}}"""
                        .formatted(Instant.now(), UUID.randomUUID(), closes[minute],
                                open.plusSeconds(60L * minute), replayId)));
            }
        }
    }

    private List<StrategySignal> awaitSignals(int count) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        List<StrategySignal> found = signals.findAll();
        while (found.size() < count && System.nanoTime() < deadline) {
            Thread.sleep(200);
            found = signals.findAll();
        }
        return found;
    }

    private void awaitPosition(long id, Position expected) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (strategies.findById(id).orElseThrow().getPosition() != expected && System.nanoTime() < deadline) {
            Thread.sleep(100);
        }
    }

    @Test
    @DisplayName("trades a switched-on strategy on replayed bars, through the trading API with its own token")
    void tradesOnReplayedBars() throws Exception {
        String trader = token("trader", "USER");
        long id = createStrategy(trader);
        mockMvc.perform(put("/api/v1/strategies/{id}/status", id).header("Authorization", trader)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // SMA 2 crosses above SMA 4 on the fifth bar, then back below on the seventh
        String replayId = UUID.randomUUID().toString();
        publishReplay(replayId, 11, 11, 10, 10, 12, 12, 9, 8);
        List<StrategySignal> fired = awaitSignals(2);
        // The position moves once the engine has waited for the SELL to settle
        awaitPosition(id, Position.FLAT);

        assertEquals(2, fired.size());
        assertEquals(List.of("BUY", "SELL"), fired.stream().map(StrategySignal::getSide).toList());
        assertEquals(2, ordersReceived.size());
        assertEquals("Bearer service-token", ordersReceived.get(0).getKey());
        JsonNode buy = objectMapper.readTree(ordersReceived.get(0).getValue());
        assertEquals(traderAccount, buy.get("accountId").asLong());
        assertEquals(3, buy.get("quantity").asInt());
        assertFalse(buy.has("price"), "a market order has no price");
        assertTrue(buy.get("idempotencyKey").asText().startsWith("stg-" + id + "-R"));

        mockMvc.perform(get("/api/v1/strategies/{id}/signals", id).header("Authorization", trader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].outcome").value("PLACED"))
                .andExpect(jsonPath("$[0].source").value("REPLAY"));
        mockMvc.perform(get("/api/v1/strategies/{id}", id).header("Authorization", trader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value("FLAT"))
                .andExpect(jsonPath("$.barsNeeded").value(5))
                .andExpect(jsonPath("$.market[0].source").value("REPLAY"))
                .andExpect(jsonPath("$.market[0].barsCollected").value(8));
        mockMvc.perform(get("/api/v1/strategies/activity").param("since", "2026-01-01T00:00:00Z")
                .header("Authorization", trader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].strategyName").value("Fast MA"));
    }

    @Test
    @DisplayName("backtests a rule on the daily prices the ETL loaded")
    void backtests() throws Exception {
        mockMvc.perform(post("/api/v1/strategies/backtests").header("Authorization", token("trader", "USER"))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"symbol": "SPY", "ruleType": "MA_CROSSOVER", "params": {"shortWindow": 5, "longWindow": 20},
                         "from": "2025-01-01", "to": "2025-12-31", "initialCash": 10000}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2025-01-01"))
                .andExpect(jsonPath("$.equityCurve.length()").value(300))
                .andExpect(jsonPath("$.rule").value("SMA 5 / SMA 20 crossover"))
                .andExpect(jsonPath("$.roundTrips").isNumber());
        mockMvc.perform(post("/api/v1/strategies/backtests").header("Authorization", token("trader", "USER"))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"symbol": "AAPL", "ruleType": "BOLLINGER", "to": "%s"}""".formatted(LocalDate.now())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PRC-404"));
    }

    @Test
    @DisplayName("keeps traders to their own strategies, admins read-only and the service's token out")
    void enforcesAccess() throws Exception {
        String trader = token("trader", "USER");
        long id = createStrategy(trader);

        mockMvc.perform(get("/api/v1/strategies/{id}", id).header("Authorization", token("someone", "USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("AUTH-403"));
        mockMvc.perform(get("/api/v1/strategies").header("Authorization", token("someone", "USER")))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/strategies").header("Authorization", token("admin", "ADMIN")))
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(put("/api/v1/strategies/{id}/status", id).header("Authorization", token("admin", "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"ACTIVE\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/strategies").header("Authorization", token("service:strategy-service", "STRATEGY")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("AUTH-403"));
        mockMvc.perform(get("/api/v1/strategies"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH-401"));
    }

    @Test
    @DisplayName("allows one active strategy per account, validates input and archives on delete")
    void managesStrategies() throws Exception {
        String trader = token("trader", "USER");
        long first = createStrategy(trader);
        long second = createStrategy(trader);
        String switchOn = "{\"status\": \"ACTIVE\"}";

        mockMvc.perform(put("/api/v1/strategies/{id}/status", first).header("Authorization", trader)
                .contentType(MediaType.APPLICATION_JSON).content(switchOn)).andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/strategies/{id}/status", second).header("Authorization", trader)
                .contentType(MediaType.APPLICATION_JSON).content(switchOn))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STR-409"));
        mockMvc.perform(post("/api/v1/strategies").header("Authorization", trader)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "<script>", "symbol": "spy", "ruleType": "MA_CROSSOVER", "quantity": 0}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VAL-422"));
        mockMvc.perform(post("/api/v1/strategies").header("Authorization", trader)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Old", "symbol": "OLD", "ruleType": "BOLLINGER", "quantity": 1}"""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("INS-404"));
        mockMvc.perform(post("/api/v1/strategies").header("Authorization", trader)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Wide", "symbol": "SPY", "ruleType": "BOLLINGER", "params": {"width": 9}, "quantity": 1}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("STR-422"));

        mockMvc.perform(delete("/api/v1/strategies/{id}", first).header("Authorization", trader))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/strategies").header("Authorization", trader))
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/v1/strategies/{id}", 999).header("Authorization", trader))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("STR-404"));
    }

    @Test
    @DisplayName("serves the OpenAPI document committed in contracts/strategy-api.json")
    void contractMatchesLiveSpec() throws Exception {
        String live = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        ObjectMapper json = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        JsonNode liveSpec = json.readTree(live);
        JsonNode committed = json.readTree(Files.readString(CONTRACT));

        boolean matches = committed.equals(SAME_NUMBER, liveSpec);
        if (!matches) {
            Files.writeString(LIVE_COPY, json.writeValueAsString(liveSpec));
        }
        assertTrue(matches, "contracts/strategy-api.json is out of date. The live spec is in strategy-service/"
                + LIVE_COPY + "; review the difference, then run from strategy-service: python3 -m json.tool "
                + "--indent 2 " + LIVE_COPY + " " + CONTRACT);
    }

    /** Numbers are equal if they have the same value (100 and 100.0) */
    private static final Comparator<JsonNode> SAME_NUMBER = (a, b) -> {
        if (a.isNumber() && b.isNumber()) {
            return a.decimalValue().compareTo(b.decimalValue());
        }
        return a.equals(b) ? 0 : 1;
    };
}
