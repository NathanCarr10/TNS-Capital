package com.neueda.leap.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.dtos.PlaceOrderRequest;
import com.neueda.leap.enums.AccountStatus;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.model.Account;
import com.neueda.leap.model.Instrument;
import com.neueda.leap.model.Order;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.InstrumentRepository;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.repositories.PositionRepository;
import com.neueda.leap.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Role and account-ownership rules:
 * ADMIN can do everything, CUSTOMER only on accounts they own (and can only
 * trade when that account is ACTIVE), other roles are forbidden.
 */
@DisplayName("Authorization Integration Tests")
public class AuthorizationIT extends AbstractIntegrationTest {

        @Autowired
        private MockMvc mockMvc;
        @Autowired
        private AccountRepository accountRepository;
        @Autowired
        private OrderRepository orderRepository;
        @Autowired
        private PositionRepository positionRepository;
        @Autowired
        private InstrumentRepository instrumentRepository;
        @Autowired
        private ObjectMapper objectMapper;
        @Autowired
        private Clock clock;

        private Account johnsAccount;
        private Account janesAccount;
        private Account franksSuspendedAccount;
        private Order janesOrder;

        @BeforeEach
        void setUp() {
                orderRepository.deleteAll();
                positionRepository.deleteAll();
                accountRepository.deleteAll();
                instrumentRepository.deleteAll();
                instrumentRepository.save(new Instrument("ACME", "Acme Corp", "EQUITY", "USD", true));

                johnsAccount = saveAccount("AUTH-1", "John Doe", "john", AccountStatus.ACTIVE);
                janesAccount = saveAccount("AUTH-2", "Jane Smith", "jane", AccountStatus.ACTIVE);
                franksSuspendedAccount = saveAccount("AUTH-3", "Frank Miller", "frank", AccountStatus.SUSPENDED);

                janesOrder = orderRepository.save(new Order(janesAccount.getId(), "ACME", OrderSide.BUY, 1,
                                new BigDecimal("10.00"), "auth-it-jane-1", clock));
        }

        private Account saveAccount(String number, String holder, String owner, AccountStatus status) {
                Account account = new Account(number, holder, new BigDecimal("1000.00"), clock);
                account.setOwnerUsername(owner);
                account.setStatus(status);
                return accountRepository.save(account);
        }

        private String orderFor(Account account, String key) throws Exception {
                return objectMapper.writeValueAsString(new PlaceOrderRequest(account.getId(), "ACME", OrderSide.BUY,
                                1, new BigDecimal("10.00"), key));
        }

        @Nested
        @DisplayName("Customer")
        @WithMockUser(username = "john", roles = "CUSTOMER")
        class CustomerTests {

                @Test
                @DisplayName("sees only their own accounts")
                void listsOnlyOwnAccounts() throws Exception {
                        mockMvc.perform(get("/api/v1/accounts"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$", hasSize(1)))
                                        .andExpect(jsonPath("$[0].accountNumber", equalTo("AUTH-1")));
                }

                @Test
                @DisplayName("can read their own account, balance and orders")
                void readsOwnAccount() throws Exception {
                        mockMvc.perform(get("/api/v1/accounts/{id}", johnsAccount.getId()))
                                        .andExpect(status().isOk());
                        mockMvc.perform(get("/api/v1/accounts/{id}/balance", johnsAccount.getId()))
                                        .andExpect(status().isOk());
                        mockMvc.perform(get("/api/v1/accounts/{id}/orders", johnsAccount.getId()))
                                        .andExpect(status().isOk());
                }

                @Test
                @DisplayName("is forbidden from another customer's account and orders")
                void cannotReadOtherAccount() throws Exception {
                        mockMvc.perform(get("/api/v1/accounts/{id}", janesAccount.getId()))
                                        .andExpect(status().isForbidden());
                        mockMvc.perform(get("/api/v1/accounts/{id}/orders", janesAccount.getId()))
                                        .andExpect(status().isForbidden());
                        mockMvc.perform(get("/api/v1/positions/{id}", janesAccount.getId()))
                                        .andExpect(status().isForbidden());
                        mockMvc.perform(get("/api/v1/orders/{id}", janesOrder.getId()))
                                        .andExpect(status().isForbidden());
                        mockMvc.perform(delete("/api/v1/orders/{id}", janesOrder.getId()))
                                        .andExpect(status().isForbidden());
                }

                @Test
                @DisplayName("GET /orders excludes other customers' orders")
                void listsOnlyOwnOrders() throws Exception {
                        mockMvc.perform(get("/api/v1/orders"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$", hasSize(0)));
                }

                @Test
                @DisplayName("can place an order on their own active account")
                void placesOrderOnOwnAccount() throws Exception {
                        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                                        .content(orderFor(johnsAccount, "auth-it-john-1")))
                                        .andExpect(status().isAccepted());
                }

                @Test
                @DisplayName("cannot place an order on someone else's account")
                void cannotPlaceOrderOnOtherAccount() throws Exception {
                        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                                        .content(orderFor(janesAccount, "auth-it-john-2")))
                                        .andExpect(status().isForbidden());
                }

                @Test
                @DisplayName("cannot open, delete or approve accounts")
                void cannotManageAccounts() throws Exception {
                        mockMvc.perform(post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"accountNumber\":\"X\",\"holderName\":\"X\",\"cashBalance\":1,\"ownerUsername\":\"john\"}"))
                                        .andExpect(status().isForbidden());
                        mockMvc.perform(delete("/api/v1/accounts/{id}", johnsAccount.getId()))
                                        .andExpect(status().isForbidden());
                        mockMvc.perform(patch("/api/v1/accounts/{id}/status", johnsAccount.getId())
                                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
                                        .andExpect(status().isForbidden());
                }

                @Test
                @DisplayName("cannot use the DLQ or manage instruments")
                void cannotUseAdminTools() throws Exception {
                        mockMvc.perform(get("/api/v1/dlq/messages")).andExpect(status().isForbidden());
                        mockMvc.perform(get("/api/v1/instruments")).andExpect(status().isOk());
                        mockMvc.perform(post("/api/v1/instruments").contentType(MediaType.APPLICATION_JSON)
                                        .content("{}"))
                                        .andExpect(status().isForbidden());
                }
        }

        @Nested
        @DisplayName("Customer with a suspended (not validated) account")
        @WithMockUser(username = "frank", roles = "CUSTOMER")
        class SuspendedCustomerTests {

                @Test
                @DisplayName("cannot place orders until an admin activates the account")
                void cannotTradeUntilActivated() throws Exception {
                        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                                        .content(orderFor(franksSuspendedAccount, "auth-it-frank-1")))
                                        .andExpect(status().isForbidden())
                                        .andExpect(jsonPath("$.errorCode", equalTo("ACC-403")));
                }
        }

        @Nested
        @DisplayName("Customer with no account")
        @WithMockUser(username = "nina", roles = "CUSTOMER")
        class NoAccountCustomerTests {

                @Test
                @DisplayName("sees nothing and cannot trade")
                void hasNoAccess() throws Exception {
                        mockMvc.perform(get("/api/v1/accounts"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$", hasSize(0)));
                        mockMvc.perform(get("/api/v1/accounts/{id}", johnsAccount.getId()))
                                        .andExpect(status().isForbidden());
                        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                                        .content(orderFor(johnsAccount, "auth-it-nina-1")))
                                        .andExpect(status().isForbidden());
                }
        }

        @Nested
        @DisplayName("Admin")
        @WithMockUser(username = "alice", roles = "ADMIN")
        class AdminTests {

                @Test
                @DisplayName("sees every account and order")
                void seesEverything() throws Exception {
                        mockMvc.perform(get("/api/v1/accounts"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$", hasSize(3)));
                        mockMvc.perform(get("/api/v1/accounts/{id}/orders", janesAccount.getId()))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$", hasSize(1)));
                        mockMvc.perform(get("/api/v1/orders/{id}", janesOrder.getId()))
                                        .andExpect(status().isOk());
                }

                @Test
                @DisplayName("can approve a suspended account")
                void activatesAccount() throws Exception {
                        mockMvc.perform(patch("/api/v1/accounts/{id}/status", franksSuspendedAccount.getId())
                                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.status", equalTo("ACTIVE")));
                }

                @Test
                @DisplayName("can open an account for a customer")
                void opensAccount() throws Exception {
                        mockMvc.perform(post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"accountNumber\":\"AUTH-4\",\"holderName\":\"Nina\",\"cashBalance\":100,\"ownerUsername\":\"nina\"}"))
                                        .andExpect(status().isCreated())
                                        .andExpect(jsonPath("$.ownerUsername", equalTo("nina")));
                }
        }

        @Nested
        @DisplayName("Guest")
        @WithMockUser(username = "bob", roles = "GUEST")
        class GuestTests {

                @Test
                @DisplayName("is forbidden from the API")
                void forbidden() throws Exception {
                        mockMvc.perform(get("/api/v1/accounts")).andExpect(status().isForbidden());
                        mockMvc.perform(get("/api/v1/orders")).andExpect(status().isForbidden());
                }
        }
}
