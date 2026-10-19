package com.neueda.leap.integration;

import com.neueda.leap.dtos.PlaceOrderRequest;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.exceptions.InsufficientFundsException;
import com.neueda.leap.model.Account;
import com.neueda.leap.model.DeadLetterMessage;
import com.neueda.leap.model.Instrument;
import com.neueda.leap.model.Order;
import com.neueda.leap.model.Position;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.DeadLetterMessageRepository;
import com.neueda.leap.repositories.InstrumentRepository;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.repositories.PositionRepository;
import com.neueda.leap.services.AccountService;
import com.neueda.leap.services.DeadLetterService;
import com.neueda.leap.services.OrderService;
import com.neueda.leap.services.ReservationService;
import com.neueda.leap.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cash and shares are held for an order from the moment the Trade Executor
 * accepts it, so a second order (or a withdrawal) cannot use them while the
 * broker works the first one, and they are given back when the order is
 * filled, cancelled or rejected.
 */
@DisplayName("Order reservations")
class OrderReservationIT extends AbstractIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private ReservationService reservations;

    @Autowired
    private DeadLetterService deadLetterService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private InstrumentRepository instrumentRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private DeadLetterMessageRepository dlqRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private Clock clock;

    @Autowired
    private MockMvc mockMvc;

    private Long accountId;

    @BeforeEach
    void setUp() {
        accountId = accountRepository.save(new Account("ACC-RSV", "Reservation Tester", new BigDecimal("1000.00"), clock))
                .getId();
        instrumentRepository.save(new Instrument("RSV", "Reservation Test Stock", "EQUITY", "USD", true));
    }

    @Test
    @DisplayName("Two BUY orders that each fit the balance but not together: the second is rejected, not the fill")
    void secondBuyCannotUseReservedCash() {
        broker.holdFills();

        UUID first = submit(OrderSide.BUY, 6, "100.00").getId();
        await().atMost(10, TimeUnit.SECONDS).until(() -> broker.heldCount() == 1);
        UUID second = submit(OrderSide.BUY, 6, "100.00").getId();

        await().atMost(10, TimeUnit.SECONDS).until(() -> orderStatus(second) == OrderStatus.REJECTED);
        assertThat(orderRepository.findById(second).orElseThrow().getStatusReason())
                .isEqualTo("Insufficient funds for buy order");
        assertThat(account().getCashBalance()).isEqualByComparingTo("1000.00");
        assertThat(account().getReservedCash()).isEqualByComparingTo("600.00");
        assertThat(broker.heldCount()).isEqualTo(1);

        broker.releaseFills();

        await().atMost(10, TimeUnit.SECONDS).until(() -> orderStatus(first) == OrderStatus.FILLED);
        assertThat(account().getCashBalance()).isEqualByComparingTo("400.00");
        assertThat(account().getReservedCash()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Two SELL orders for more shares than are held together: the second is rejected")
    void secondSellCannotUseReservedShares() {
        positionRepository.save(new Position(accountId, "RSV", 10, new BigDecimal("50.00")));
        broker.holdFills();

        UUID first = submit(OrderSide.SELL, 6, "100.00").getId();
        await().atMost(10, TimeUnit.SECONDS).until(() -> broker.heldCount() == 1);
        UUID second = submit(OrderSide.SELL, 6, "100.00").getId();

        await().atMost(10, TimeUnit.SECONDS).until(() -> orderStatus(second) == OrderStatus.REJECTED);
        assertThat(position().getQuantity()).isEqualTo(10);
        assertThat(position().getReservedQuantity()).isEqualTo(6);

        broker.releaseFills();

        await().atMost(10, TimeUnit.SECONDS).until(() -> orderStatus(first) == OrderStatus.FILLED);
        assertThat(position().getQuantity()).isEqualTo(4);
        assertThat(position().getReservedQuantity()).isZero();
        assertThat(account().getCashBalance()).isEqualByComparingTo("1600.00");
    }

    @Test
    @DisplayName("The API shows what open orders reserved and what is left to trade")
    void apiShowsReservations() throws Exception {
        positionRepository.save(new Position(accountId, "RSV", 10, new BigDecimal("50.00")));
        broker.holdFills();
        submit(OrderSide.BUY, 3, "100.00");
        submit(OrderSide.SELL, 4, "100.00");
        await().atMost(10, TimeUnit.SECONDS).until(() -> broker.heldCount() == 2);

        mockMvc.perform(get("/api/v1/accounts/{accountId}", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashBalance", is(1000.0)))
                .andExpect(jsonPath("$.reservedCash", is(300.0)))
                .andExpect(jsonPath("$.availableCash", is(700.0)));
        mockMvc.perform(get("/api/v1/accounts/{accountId}/balance", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", is(1000.0)))
                .andExpect(jsonPath("$.reservedCash", is(300.0)))
                .andExpect(jsonPath("$.availableCash", is(700.0)));
        mockMvc.perform(get("/api/v1/accounts/{accountId}/positions", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].quantity", is(10)))
                .andExpect(jsonPath("$[0].reservedQuantity", is(4)))
                .andExpect(jsonPath("$[0].availableQuantity", is(6)));
    }

    @Test
    @DisplayName("Cash reserved for an order cannot be withdrawn")
    void reservedCashCannotBeWithdrawn() {
        broker.holdFills();
        submit(OrderSide.BUY, 6, "100.00");
        await().atMost(10, TimeUnit.SECONDS).until(() -> broker.heldCount() == 1);

        BigDecimal tooMuch = new BigDecimal("400.01");
        assertThatThrownBy(() -> accountService.withdraw(accountId, tooMuch))
                .isInstanceOf(InsufficientFundsException.class);
        accountService.withdraw(accountId, new BigDecimal("400.00"));

        assertThat(account().getCashBalance()).isEqualByComparingTo("600.00");
        assertThat(account().getReservedCash()).isEqualByComparingTo("600.00");
    }

    @Test
    @DisplayName("Cancelling an order releases its cash; its late fill is ignored")
    void cancellationReleasesCash() {
        broker.holdFills();
        UUID orderId = submit(OrderSide.BUY, 6, "100.00").getId();
        await().atMost(10, TimeUnit.SECONDS).until(() -> broker.heldCount() == 1);
        assertThat(account().getReservedCash()).isEqualByComparingTo("600.00");

        orderService.cancelOrder(orderId);

        assertThat(account().getReservedCash()).isEqualByComparingTo("0.00");
        assertThat(orderRepository.findById(orderId).orElseThrow().isReserved()).isFalse();

        broker.releaseFills();
        // The fill for the cancelled order moves nothing
        await().during(2, TimeUnit.SECONDS).atMost(4, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.CANCELLED);
            assertThat(account().getCashBalance()).isEqualByComparingTo("1000.00");
            assertThat(account().getReservedCash()).isEqualByComparingTo("0.00");
        });
    }

    @Test
    @DisplayName("A rejection releases the cash, and replaying the DLQ message reserves it again")
    void rejectionReleasesAndReplayReservesAgain() {
        broker.goDown();
        UUID orderId = submit(OrderSide.BUY, 6, "100.00").getId();

        await().atMost(10, TimeUnit.SECONDS).until(() -> orderStatus(orderId) == OrderStatus.REJECTED
                && !dlqRepository.findAll().isEmpty());
        assertThat(account().getReservedCash()).isEqualByComparingTo("0.00");
        assertThat(orderRepository.findById(orderId).orElseThrow().isReserved()).isFalse();

        // The broker is back, but holds its fill so the replayed order is seen holding cash
        broker.reset();
        broker.holdFills();
        DeadLetterMessage dlqMessage = dlqRepository.findAll().get(0);
        assertThat(deadLetterService.replayMessage(dlqMessage.getId(), orderService)).isTrue();

        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.NEW);
        assertThat(account().getReservedCash()).isEqualByComparingTo("600.00");

        broker.releaseFills();
        await().atMost(10, TimeUnit.SECONDS).until(() -> orderStatus(orderId) == OrderStatus.FILLED);
        assertThat(account().getCashBalance()).isEqualByComparingTo("400.00");
        assertThat(account().getReservedCash()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Concurrent reservations on one account never hold more than its balance")
    void concurrentReservationsNeverOverbook() throws Exception {
        // 20 orders of 100.00 race for 1000.00: exactly 10 may win
        int attempts = 20;
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            Order order = new Order(accountId, "RSV", OrderSide.BUY, 1, new BigDecimal("100.00"),
                    "RACE-" + i, clock);
            tasks.add(() -> {
                start.await();
                try {
                    tx.executeWithoutResult(status -> reservations.reserve(order));
                    return true;
                } catch (InsufficientFundsException ex) {
                    return false;
                }
            });
        }

        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (Callable<Boolean> task : tasks) {
                results.add(pool.submit(task));
            }
            start.countDown();
            int reserved = 0;
            for (Future<Boolean> result : results) {
                if (result.get(30, TimeUnit.SECONDS)) {
                    reserved++;
                }
            }

            assertThat(reserved).isEqualTo(10);
            assertThat(account().getReservedCash()).isEqualByComparingTo("1000.00");
            assertThat(account().getAvailableCash()).isEqualByComparingTo("0.00");
        } finally {
            pool.shutdownNow();
        }
    }

    private Order submit(OrderSide side, int quantity, String price) {
        return orderService.submitOrder(new PlaceOrderRequest(accountId, "RSV", side, quantity,
                new BigDecimal(price), UUID.randomUUID().toString()));
    }

    private OrderStatus orderStatus(UUID orderId) {
        return orderRepository.findById(orderId).orElseThrow().getStatus();
    }

    private Account account() {
        return accountRepository.findById(accountId).orElseThrow();
    }

    private Position position() {
        return positionRepository.findByAccountIdAndSymbol(accountId, "RSV").orElseThrow();
    }
}
