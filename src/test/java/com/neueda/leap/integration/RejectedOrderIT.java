package com.neueda.leap.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.kafka.events.MessageEnvelope;
import com.neueda.leap.kafka.events.OrderEvent;
import com.neueda.leap.model.Account;
import com.neueda.leap.model.DeadLetterMessage;
import com.neueda.leap.model.Instrument;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.DeadLetterMessageRepository;
import com.neueda.leap.repositories.InstrumentRepository;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.services.DeadLetterService;
import com.neueda.leap.services.OrderService;
import com.neueda.leap.time.Clock;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for failed (REJECTED) orders consumed from Kafka.
 *
 * Verifies that a failed order is persisted as REJECTED, produces exactly one
 * trade event and one DLQ entry, and can be replayed once the cause is fixed.
 */
@DisplayName("Rejected Order Integration Tests")
public class RejectedOrderIT extends AbstractIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private InstrumentRepository instrumentRepository;

    @Autowired
    private DeadLetterMessageRepository dlqRepository;

    @Autowired
    private DeadLetterService deadLetterService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Clock clock;

    private Account testAccount;
    private KafkaConsumer<String, String> tradeEventsConsumer;
    private final List<JsonNode> tradeEvents = new ArrayList<>();

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        dlqRepository.deleteAll();
        accountRepository.deleteAll();
        instrumentRepository.deleteAll();

        testAccount = accountRepository.save(
                new Account("ACC_REJECTED_TEST", "Rejected Order Tester", new BigDecimal("1000.00"), clock));
        instrumentRepository.save(new Instrument("REJ", "Rejected Test Stock", "EQUITY", "USD", true));

        tradeEventsConsumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "rejected-order-it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
                new StringDeserializer(), new StringDeserializer());
        tradeEventsConsumer.subscribe(List.of("trade-events"));
    }

    @AfterEach
    void tearDown() {
        tradeEventsConsumer.close();
    }

    @Test
    @DisplayName("Failed order is saved as REJECTED with exactly one trade event and DLQ entry, and can be replayed")
    void testRejectedOrderIsPersistedOnceAndReplayable() throws Exception {
        UUID orderId = UUID.randomUUID();
        // 100 x 50.00 = 5000.00 > 1000.00 balance -> InsufficientFundsException
        OrderEvent event = new OrderEvent(orderId, testAccount.getId(), "REJ", OrderSide.BUY, 100,
                new BigDecimal("50.00"), clock.now());
        MessageEnvelope<OrderEvent> envelope = new MessageEnvelope<>(clock.now(), UUID.randomUUID().toString(),
                "1.0", "ORDER_ACCEPTED", event);

        kafkaTemplate.send("orders", testAccount.getId().toString(), objectMapper.writeValueAsString(envelope));

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(orderRepository.findById(orderId))
                    .get()
                    .extracting(order -> order.getStatus())
                    .isEqualTo(OrderStatus.REJECTED);
            assertThat(dlqRepository.findAll()).hasSize(1);
        });

        // Keep consuming for a while so redelivered/duplicate events would show up
        pollTradeEvents(Duration.ofSeconds(5));
        assertThat(eventsFor(orderId))
                .extracting(e -> e.at("/payload/payload/status").asText())
                .containsExactly("REJECTED");
        assertThat(accountRepository.findById(testAccount.getId()).orElseThrow().getCashBalance())
                .isEqualByComparingTo("1000.00");

        // Fund the account and replay the DLQ message: the REJECTED order is re-executed
        Account account = accountRepository.findById(testAccount.getId()).orElseThrow();
        account.credit(new BigDecimal("10000.00"));
        accountRepository.save(account);

        DeadLetterMessage dlqMessage = dlqRepository.findAll().get(0);
        assertThat(deadLetterService.replayMessage(dlqMessage.getId(), orderService)).isTrue();

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.FILLED);
        pollTradeEvents(Duration.ofSeconds(3));
        assertThat(eventsFor(orderId))
                .extracting(e -> e.at("/payload/payload/status").asText())
                .containsExactly("REJECTED", "FILLED");
    }

    private void pollTradeEvents(Duration duration) throws Exception {
        long deadline = System.currentTimeMillis() + duration.toMillis();
        while (System.currentTimeMillis() < deadline) {
            for (ConsumerRecord<String, String> record : tradeEventsConsumer.poll(Duration.ofMillis(250))) {
                tradeEvents.add(objectMapper.readTree(record.value()));
            }
        }
    }

    private List<JsonNode> eventsFor(UUID orderId) {
        return tradeEvents.stream()
                .filter(e -> orderId.toString().equals(e.at("/payload/payload/orderId").asText()))
                .toList();
    }
}
