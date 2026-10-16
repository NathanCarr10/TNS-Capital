package com.neueda.leap.integration;

import com.neueda.leap.dtos.HolderName;
import com.neueda.leap.model.Account;
import com.neueda.leap.model.Instrument;
import com.neueda.leap.model.Position;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.InstrumentRepository;
import com.neueda.leap.repositories.PositionRepository;
import com.neueda.leap.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration Tests for AccountController
 * Tests HTTP endpoints for account management with a real database
 */
@DisplayName("Account Controller Integration Tests")
@WithMockUser(username = "testuser", roles = "ADMIN")
class AccountControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private InstrumentRepository instrumentRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private Clock clock;

    private Account testAccount;

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();

        testAccount = new Account("ACC001", "John Doe", new BigDecimal("10000.00"), clock);
        testAccount = accountRepository.save(testAccount);
    }

    @Test
    @DisplayName("Should retrieve all accounts")
    @SuppressWarnings("null")
    void testGetAllAccounts() throws Exception {
        // Create another account for testing
        Account secondAccount = new Account("ACC002", "Jane Smith", new BigDecimal("25000.00"), clock);
        accountRepository.save(secondAccount);

        mockMvc.perform(get("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].holderName", notNullValue()))
                .andExpect(jsonPath("$[0].cashBalance", notNullValue()))
                .andExpect(jsonPath("$[0].status", equalTo("ACTIVE")));
    }

    @Test
    @DisplayName("Should retrieve a specific account by ID")
    @SuppressWarnings("null")
    void testGetAccountById() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{accountId}", testAccount.getId())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", equalTo(testAccount.getId().intValue())))
                .andExpect(jsonPath("$.holderName", equalTo("John Doe")))
                .andExpect(jsonPath("$.status", equalTo("ACTIVE")))
                .andExpect(jsonPath("$.cashBalance", is(10000.0)));
    }

    @Test
    @DisplayName("Should return 404 when account not found")
    @SuppressWarnings("null")
    void testGetAccountNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/99999")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should retrieve account balance")
    @SuppressWarnings("null")
    void testGetAccountBalance() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{accountId}/balance", testAccount.getId())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", is(10000.0)));
    }

    @Test
    @DisplayName("Should return empty positions list for new account")
    @SuppressWarnings("null")
    void testGetAccountPositions() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{accountId}/positions", testAccount.getId())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Should list positions with no market value when there is no live price")
    void testPositionsWithoutLivePrice() throws Exception {
        // Tests run without Alpaca keys, so there are no live prices
        instrumentRepository.save(new Instrument("ACME", "Acme Corp", "Equity", "USD", true));
        positionRepository.save(new Position(testAccount.getId(), "ACME", 10, new BigDecimal("25.00")));

        mockMvc.perform(get("/api/v1/accounts/{accountId}/positions", testAccount.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol", is("ACME")))
                .andExpect(jsonPath("$[0].averageCost", is(25.0)))
                .andExpect(jsonPath("$[0].marketValue", nullValue()));
    }

    @Test
    @DisplayName("Should move lastUpdated when the balance changes")
    void testDepositUpdatesLastUpdated() throws Exception {
        java.time.Instant created = accountRepository.findById(testAccount.getId()).orElseThrow().getLastUpdated();

        mockMvc.perform(post("/api/v1/accounts/{accountId}/deposit", testAccount.getId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\": 5.00}"))
                .andExpect(status().isOk());

        java.time.Instant updated = accountRepository.findById(testAccount.getId()).orElseThrow().getLastUpdated();
        org.junit.jupiter.api.Assertions.assertTrue(updated.isAfter(created),
                "lastUpdated should move from " + created + " but was " + updated);
    }

    @Test
    @DisplayName("Should return empty orders list for new account")
    @SuppressWarnings("null")
    void testGetAccountOrders() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{accountId}/orders", testAccount.getId())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Should return 404 when getting balance for non-existent account")
    @SuppressWarnings("null")
    void testGetBalanceAccountNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/99999/balance")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should return 404 when getting positions for non-existent account")
    @SuppressWarnings("null")
    void testGetPositionsAccountNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/99999/positions")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should return 404 when getting orders for non-existent account")
    @SuppressWarnings("null")
    void testGetOrdersAccountNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/99999/orders")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should open a new account")
    void testCreateAccount() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountNumber\":\"ACC002\",\"holderName\":\"Jane Roe\",\"cashBalance\":500.00}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountNumber", is("ACC002")))
                .andExpect(jsonPath("$.status", is("ACTIVE")));
    }

    @Test
    @DisplayName("Should return ACC-409 for a duplicate account number")
    void testCreateAccountDuplicateNumber() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountNumber\":\"ACC001\",\"holderName\":\"Someone Else\",\"cashBalance\":1.00}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", is("ACC-409")));
    }

    @Test
    @DisplayName("Should update the holder name and keep the opening date")
    void testUpdateHolderName() throws Exception {
        java.time.Instant openedOn = accountRepository.findById(testAccount.getId()).orElseThrow().getOpenedOn();

        mockMvc.perform(patch("/api/v1/accounts/{id}", testAccount.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"holderName\":\"Johnny Doe\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holderName", is("Johnny Doe")))
                .andExpect(jsonPath("$.openedOn", notNullValue()));

        assertEquals(openedOn, accountRepository.findById(testAccount.getId()).orElseThrow().getOpenedOn());
    }

    @Test
    @DisplayName("Should reject a script payload as the holder name when opening an account")
    void testCreateAccountRejectsScriptInHolderName() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountNumber\":\"ACC003\",\"holderName\":\"<script>alert(1)</script>\",\"cashBalance\":1.00}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode", is("VAL-422")));
    }

    @Test
    @DisplayName("Should reject a script payload as the new holder name")
    void testUpdateHolderNameRejectsScript() throws Exception {
        mockMvc.perform(patch("/api/v1/accounts/{id}", testAccount.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"holderName\":\"<img src=x onerror=alert(1)>\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode", is("VAL-422")))
                .andExpect(jsonPath("$.message", is("holderName: " + HolderName.MESSAGE)));
    }

    @Test
    @DisplayName("Should close an emptied account and keep the record")
    void testCloseAccountKeepsRecord() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/{id}/withdraw", testAccount.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": 10000.00}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/accounts/{id}", testAccount.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/accounts/{id}", testAccount.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CLOSED")));
    }

    @Test
    @DisplayName("Should refuse to close an account that still holds cash, with ACC-409")
    void testCloseAccountWithCash() throws Exception {
        mockMvc.perform(delete("/api/v1/accounts/{id}", testAccount.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", is("ACC-409")))
                .andExpect(jsonPath("$.message", containsString("withdraw")));

        mockMvc.perform(get("/api/v1/accounts/{id}", testAccount.getId()))
                .andExpect(jsonPath("$.status", is("ACTIVE")));
    }

    @Test
    @DisplayName("Should refuse to close an account that still holds positions, with ACC-409")
    void testCloseAccountWithHoldings() throws Exception {
        instrumentRepository.save(new Instrument("ACME", "Acme Corp", "Equity", "USD", true));
        positionRepository.save(new Position(testAccount.getId(), "ACME", 10, new BigDecimal("25.00")));
        mockMvc.perform(post("/api/v1/accounts/{id}/withdraw", testAccount.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": 10000.00}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/accounts/{id}", testAccount.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", is("ACC-409")))
                .andExpect(jsonPath("$.message", containsString("holding")));
    }
}
