package com.neueda.leap.integration;

import com.neueda.leap.model.Account;
import com.neueda.leap.repositories.AccountRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration Tests for AccountController
 * Tests HTTP endpoints for account management with a real database
 */
@DisplayName("Account Controller Integration Tests")
@WithMockUser(username = "testuser", roles = "USER")
public class AccountControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

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
    void testGetAccountNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/99999")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should retrieve account balance")
    void testGetAccountBalance() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{accountId}/balance", testAccount.getId())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", is(10000.0)));
    }

    @Test
    @DisplayName("Should return empty positions list for new account")
    void testGetAccountPositions() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{accountId}/positions", testAccount.getId())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Should return empty orders list for new account")
    void testGetAccountOrders() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{accountId}/orders", testAccount.getId())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Should return 404 when getting balance for non-existent account")
    void testGetBalanceAccountNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/99999/balance")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should return 404 when getting positions for non-existent account")
    void testGetPositionsAccountNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/99999/positions")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should return 404 when getting orders for non-existent account")
    void testGetOrdersAccountNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/99999/orders")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
