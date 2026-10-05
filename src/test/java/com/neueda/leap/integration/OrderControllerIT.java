package com.neueda.leap.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.dtos.PlaceOrderRequest;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.model.Account;
import com.neueda.leap.model.Instrument;
import com.neueda.leap.model.Order;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.InstrumentRepository;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration Tests for OrderController
 * Tests HTTP endpoints for order placement and retrieval with a real database
 */
@DisplayName("Order Controller Integration Tests")
@WithMockUser(username = "testuser", roles = "USER")
public class OrderControllerIT extends AbstractIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private OrderRepository orderRepository;

        @Autowired
        private AccountRepository accountRepository;

        @Autowired
        private InstrumentRepository instrumentRepository;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private Clock clock;

        private Account testAccount;

        @BeforeEach
        void setUp() {
                orderRepository.deleteAll();
                accountRepository.deleteAll();
                instrumentRepository.deleteAll();

                // Create test account
                testAccount = new Account("ACC001", "Jane Trader", new BigDecimal("50000.00"), clock);
                testAccount = accountRepository.save(testAccount);

                // Create test instrument (required for order placement validation)
                Instrument aapl = new Instrument("AAPL", "Apple Inc.", "EQUITY", "USD", true);
                instrumentRepository.save(aapl);
        }

        @Test
        @DisplayName("Should retrieve all orders")
        @SuppressWarnings("null")
        void testGetAllOrders() throws Exception {
                // Create an order first
                Order order = new Order(
                                testAccount.getId(),
                                "AAPL",
                                OrderSide.BUY,
                                10,
                                new BigDecimal("150.00"),
                                "ORDER-001",
                                clock);
                orderRepository.save(order);

                mockMvc.perform(get("/api/v1/orders")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", hasSize(1)))
                                .andExpect(jsonPath("$[0].symbol", equalTo("AAPL")))
                                .andExpect(jsonPath("$[0].side", equalTo("BUY")))
                                .andExpect(jsonPath("$[0].quantity", equalTo(10)))
                                .andExpect(jsonPath("$[0].status", equalTo("NEW")));
        }

        @Test
        @DisplayName("Should retrieve a specific order by ID")
        @SuppressWarnings("null")
        void testGetOrderById() throws Exception {
                // Create an order
                Order order = new Order(
                                testAccount.getId(),
                                "AAPL",
                                OrderSide.BUY,
                                5,
                                new BigDecimal("155.50"),
                                "ORDER-002",
                                clock);
                orderRepository.save(order);

                mockMvc.perform(get("/api/v1/orders/{orderId}", order.getId())
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id", equalTo(order.getId().toString())))
                                .andExpect(jsonPath("$.accountId", equalTo(testAccount.getId().intValue())))
                                .andExpect(jsonPath("$.symbol", equalTo("AAPL")))
                                .andExpect(jsonPath("$.side", equalTo("BUY")))
                                .andExpect(jsonPath("$.quantity", equalTo(5)));
        }

        @Test
        @DisplayName("Should return 404 when order not found")
        @SuppressWarnings("null")
        void testGetOrderNotFound() throws Exception {
                UUID nonExistentOrderId = UUID.randomUUID();
                mockMvc.perform(get("/api/v1/orders/{orderId}", nonExistentOrderId)
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isNotFound());
        }

        private String placeOrder(PlaceOrderRequest request) throws Exception {
                String body = mockMvc.perform(post("/api/v1/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isAccepted())
                                .andExpect(jsonPath("$.status", equalTo("ACCEPTED")))
                                .andReturn().getResponse().getContentAsString();
                return objectMapper.readTree(body).get("orderId").asText();
        }

        @Test
        @DisplayName("Should accept a new order and fill it asynchronously")
        @SuppressWarnings("null")
        void testPlaceOrderSuccess() throws Exception {
                PlaceOrderRequest request = new PlaceOrderRequest(
                                testAccount.getId(),
                                "AAPL",
                                OrderSide.BUY,
                                10,
                                new BigDecimal("152.00"),
                                "IDEM-001");

                String orderId = placeOrder(request);

                await().pollInSameThread().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> mockMvc
                                .perform(get("/api/v1/orders/{orderId}", orderId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id", equalTo(orderId)))
                                .andExpect(jsonPath("$.accountId", equalTo(testAccount.getId().intValue())))
                                .andExpect(jsonPath("$.symbol", equalTo("AAPL")))
                                .andExpect(jsonPath("$.side", equalTo("BUY")))
                                .andExpect(jsonPath("$.quantity", equalTo(10)))
                                .andExpect(jsonPath("$.price", is(152.0)))
                                .andExpect(jsonPath("$.status", equalTo("FILLED"))));
        }

        @Test
        @DisplayName("Should return ORD-409 when an idempotency key is reused")
        void testPlaceOrderDuplicateKeyRejected() throws Exception {
                PlaceOrderRequest request = new PlaceOrderRequest(
                                testAccount.getId(), "AAPL", OrderSide.BUY, 1, new BigDecimal("150.00"), "IDEM-DUP");

                String orderId = placeOrder(request);
                await().atMost(10, TimeUnit.SECONDS)
                                .until(() -> orderRepository.findById(UUID.fromString(orderId)).isPresent());

                mockMvc.perform(post("/api/v1/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.errorCode", equalTo("ORD-409")));

                assertThat(orderRepository.findAll()).hasSize(1);
        }

        @Test
        @DisplayName("Should store an unaffordable order as REJECTED with the reason")
        void testPlaceOrderInsufficientFundsRejected() throws Exception {
                PlaceOrderRequest request = new PlaceOrderRequest(
                                testAccount.getId(), "AAPL", OrderSide.BUY, 1000, new BigDecimal("150.00"),
                                "IDEM-POOR");

                String orderId = placeOrder(request);

                await().pollInSameThread().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> mockMvc
                                .perform(get("/api/v1/orders/{orderId}", orderId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status", equalTo("REJECTED")))
                                .andExpect(jsonPath("$.statusReason", containsString("Insufficient funds"))));
        }

        @Test
        @DisplayName("Should return ORD-409 when cancelling a filled order")
        void testCancelFilledOrderConflict() throws Exception {
                Order filled = new Order(testAccount.getId(), "AAPL", OrderSide.BUY, 1, new BigDecimal("150.00"),
                                "IDEM-FILLED", clock);
                filled.setStatus(com.neueda.leap.enums.OrderStatus.FILLED);
                orderRepository.save(filled);

                mockMvc.perform(delete("/api/v1/orders/{orderId}", filled.getId()))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.errorCode", equalTo("ORD-409")));
        }

        @Test
        @DisplayName("Should cancel a NEW order and return it with status CANCELLED")
        void testCancelNewOrder() throws Exception {
                Order working = new Order(testAccount.getId(), "AAPL", OrderSide.BUY, 1, new BigDecimal("150.00"),
                                "IDEM-WORKING", clock);
                orderRepository.save(working);

                mockMvc.perform(delete("/api/v1/orders/{orderId}", working.getId()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status", equalTo("CANCELLED")));
        }

        @Test
        @DisplayName("Should return 404 when placing order for non-existent account")
        @SuppressWarnings("null")
        void testPlaceOrderAccountNotFound() throws Exception {
                PlaceOrderRequest request = new PlaceOrderRequest(
                                99999L,
                                "AAPL",
                                OrderSide.BUY,
                                10,
                                new BigDecimal("152.00"),
                                "IDEM-002");

                mockMvc.perform(post("/api/v1/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 422 for invalid order request")
        @SuppressWarnings("null")
        void testPlaceOrderInvalidRequest() throws Exception {
                String invalidRequest = "{\"accountId\": null, \"symbol\": \"\"}";

                mockMvc.perform(post("/api/v1/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invalidRequest))
                                .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("Should return empty orders list initially")
        @SuppressWarnings("null")
        void testGetAllOrdersEmpty() throws Exception {
                mockMvc.perform(get("/api/v1/orders")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", hasSize(0)));
        }
}
