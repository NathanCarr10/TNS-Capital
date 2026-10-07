package com.neueda.leap.integration;

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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * A user may only use the account linked to them in users.account_id (opened
 * by the auth service at registration); an admin may use every account.
 */
@DisplayName("Account ownership")
class AccountOwnershipIT extends AbstractIntegrationTest {

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

    private Account ownAccount;
    private Account otherAccount;
    private Order otherOrder;

    @BeforeEach
    void setUp() {
        ownAccount = accountRepository.save(new Account("ACC-U000001", "Trader", new BigDecimal("1000.00"), clock));
        otherAccount = accountRepository.save(new Account("ACC-U000002", "Someone Else", new BigDecimal("500.00"), clock));
        linkUser("trader", ownAccount);
        linkUser("someone", otherAccount);

        instrumentRepository.save(new Instrument("AAPL", "Apple Inc.", "EQUITY", "USD", true));
        otherOrder = orderRepository.save(new Order(otherAccount.getId(), "AAPL", OrderSide.BUY, 1,
                new BigDecimal("100.00"), UUID.randomUUID().toString(), clock));
    }

    private void linkUser(String username, Account account) {
        jdbcTemplate.update("INSERT INTO users (username, password_hash, role, account_id) VALUES (?, 'x', 'USER', ?)",
                username, account.getId());
    }

    private static String cash(String amount) {
        return "{\"amount\": " + amount + "}";
    }

    private static String buyOrder(Long accountId) {
        return """
                {"accountId": %d, "symbol": "AAPL", "side": "BUY", "quantity": 1,
                 "price": 100.00, "idempotencyKey": "%s"}""".formatted(accountId, UUID.randomUUID());
    }

    @Nested
    @DisplayName("as a user")
    @WithMockUser(username = "trader", roles = "USER")
    class AsUser {

        @Test
        @DisplayName("lists only their own account")
        void listsOwnAccountOnly() throws Exception {
            mockMvc.perform(get("/api/v1/accounts"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].id").value(ownAccount.getId().intValue()));
        }

        @Test
        @DisplayName("can read their own account and balance")
        void readsOwnAccount() throws Exception {
            mockMvc.perform(get("/api/v1/accounts/{id}", ownAccount.getId()))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/accounts/{id}/balance", ownAccount.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.balance").value(1000.0));
        }

        @Test
        @DisplayName("is refused every read of another user's account")
        void cannotReadOtherAccount() throws Exception {
            for (String path : new String[] { "", "/balance", "/positions", "/orders" }) {
                mockMvc.perform(get("/api/v1/accounts/{id}" + path, otherAccount.getId()))
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.errorCode").value("AUTH-403"));
            }
            mockMvc.perform(get("/api/v1/positions/{id}", otherAccount.getId()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/orders/{id}/history", otherAccount.getId()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("is refused changes to another user's account")
        void cannotChangeOtherAccount() throws Exception {
            mockMvc.perform(patch("/api/v1/accounts/{id}", otherAccount.getId())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"holderName\": \"Hijacked\"}"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/v1/accounts/{id}", otherAccount.getId()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/v1/accounts/{id}/withdraw", otherAccount.getId())
                    .contentType(MediaType.APPLICATION_JSON).content(cash("100")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("can deposit to and withdraw from their own account")
        void movesCashOnOwnAccount() throws Exception {
            mockMvc.perform(post("/api/v1/accounts/{id}/deposit", ownAccount.getId())
                    .contentType(MediaType.APPLICATION_JSON).content(cash("250.50")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cashBalance").value(1250.5));
            mockMvc.perform(post("/api/v1/accounts/{id}/withdraw", ownAccount.getId())
                    .contentType(MediaType.APPLICATION_JSON).content(cash("1250.50")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cashBalance").value(0.0));
        }

        @Test
        @DisplayName("cannot withdraw more than the balance")
        void cannotOverdraw() throws Exception {
            mockMvc.perform(post("/api/v1/accounts/{id}/withdraw", ownAccount.getId())
                    .contentType(MediaType.APPLICATION_JSON).content(cash("1000.01")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("ORD-400"));
        }

        @Test
        @DisplayName("cannot open accounts; that is an admin task")
        void cannotCreateAccounts() throws Exception {
            mockMvc.perform(post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"accountNumber\": \"ACC-X\", \"holderName\": \"X\", \"cashBalance\": 0}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("cannot place an order on another user's account")
        void cannotOrderOnOtherAccount() throws Exception {
            mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                    .content(buyOrder(otherAccount.getId())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("gets 403, not 404, for an account that does not exist")
        void unknownAccountIsForbidden() throws Exception {
            mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                    .content(buyOrder(999_999L)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("can place an order on their own account")
        void ordersOnOwnAccount() throws Exception {
            mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                    .content(buyOrder(ownAccount.getId())))
                    .andExpect(status().isAccepted());
        }

        @Test
        @DisplayName("sees only their own orders and cannot touch another user's order")
        void cannotSeeOtherOrders() throws Exception {
            mockMvc.perform(get("/api/v1/orders"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", empty()));
            mockMvc.perform(get("/api/v1/orders/{id}", otherOrder.getId()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/v1/orders/{id}", otherOrder.getId()))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("as an admin")
    @WithMockUser(username = "admin", roles = "ADMIN")
    class AsAdmin {

        @Test
        @DisplayName("lists every account and order")
        void listsEverything() throws Exception {
            mockMvc.perform(get("/api/v1/accounts"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)));
            mockMvc.perform(get("/api/v1/orders"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)));
        }

        @Test
        @DisplayName("can use any user's account")
        void usesAnyAccount() throws Exception {
            mockMvc.perform(get("/api/v1/accounts/{id}/balance", otherAccount.getId()))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/api/v1/accounts/{id}/deposit", otherAccount.getId())
                    .contentType(MediaType.APPLICATION_JSON).content(cash("100")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cashBalance").value(600.0));
            mockMvc.perform(get("/api/v1/orders/{id}", otherOrder.getId()))
                    .andExpect(status().isOk());
        }
    }
}
