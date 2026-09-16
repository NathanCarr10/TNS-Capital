package com.neueda.leap.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.neueda.leap.enums.AccountStatus;
import com.neueda.leap.exceptions.InsufficientFundsException;

@DisplayName("Account Test Suite")

class AccountTest {
    private Account account;

    @BeforeEach
    void setUp() {
        account = new Account("ACC001", "John Doe", new BigDecimal("10000.00"));
    }

    @DisplayName("Constructor initializes all fields correctly")
    @Test
    void testAccountConstructor() {
        assertEquals("ACC001", account.getAccountId(), "Account ID should match constructor argument");
        assertEquals("John Doe", account.getHolderName(), "Holder name should match constructor argument");
        assertEquals(new BigDecimal("10000.00"), account.getCashBalance(), "Initial cash balance should match constructor argument");
        assertEquals(AccountStatus.ACTIVE, account.getStatus(), "New account should have ACTIVE status");
        assertEquals(0, account.getVersion(), "Initial version should be 0");
        assertNotNull(account.getLastUpdated(), "Last updated timestamp should be set");
    }

    @DisplayName("Debit Operation Tests")
    @Nested
    class DebitTests {
        @DisplayName("Debit with valid amount reduces balance correctly")
        @ParameterizedTest(name = "Debit {0} from 10000.00")
        @ValueSource(strings = {"0.01", "1000.00", "5000.00", "9999.99"})
        void testDebitValidAmounts(String amount) throws InsufficientFundsException {
            BigDecimal debitAmount = new BigDecimal(amount);
            BigDecimal expectedBalance = new BigDecimal("10000.00").subtract(debitAmount);
            account.debit(debitAmount);
            assertEquals(expectedBalance, account.getCashBalance(), 
                "Balance should be reduced by debit amount");
        }

        @DisplayName("Debit amount exceeding balance throws InsufficientFundsException")
        @Test
        void testDebitInsufficientFunds() {
            assertThrows(InsufficientFundsException.class, () -> {
                account.debit(new BigDecimal("15000.00"));
            }, "Should throw exception when debiting amount exceeds balance");
        }

        @DisplayName("Debit with amount slightly exceeding balance throws exception")
        @Test
        void testDebitBoundaryExceedsBalance() {
            assertThrows(InsufficientFundsException.class, () -> {
                account.debit(new BigDecimal("10000.01"));
            }, "Should throw exception even when exceeding by minimal amount");
        }

        @DisplayName("Debit exact balance leaves account with zero")
        @Test
        void testDebitExactAmount() throws InsufficientFundsException {
            account.debit(new BigDecimal("10000.00"));
            assertEquals(new BigDecimal("0.00"), account.getCashBalance(), 
                "Balance should be exactly 0.00 after debiting entire balance");
        }
    }

    @DisplayName("Credit Operation Tests")
    @Nested
    class CreditTests {
        @DisplayName("Credit with valid amounts increases balance correctly")
        @ParameterizedTest(name = "Credit {0} to 10000.00")
        @ValueSource(strings = {"0.01", "500.00", "5000.00", "50000.00"})
        void testCreditValidAmounts(String amount) {
            BigDecimal creditAmount = new BigDecimal(amount);
            BigDecimal expectedBalance = new BigDecimal("10000.00").add(creditAmount);
            account.credit(creditAmount);
            assertEquals(expectedBalance, account.getCashBalance(), 
                "Balance should be increased by credit amount");
        }

        @DisplayName("Multiple consecutive debits and credits are applied correctly")
        @Test
        void testMultipleDebitsAndCredits() throws InsufficientFundsException {
            account.debit(new BigDecimal("2000.00"));
            account.credit(new BigDecimal("3000.00"));
            account.debit(new BigDecimal("1500.00"));
            assertEquals(new BigDecimal("9500.00"), account.getCashBalance(), 
                "Final balance should be 10000 - 2000 + 3000 - 1500 = 9500");
        }
    }

    @DisplayName("Account Status Tests")
    @Nested
    class StatusTests {
        @DisplayName("Account with ACTIVE status returns true for isActive()")
        @Test
        void testIsActiveWhenStatusActive() {
            assertTrue(account.isActive(), "Account should be active when status is ACTIVE");
        }

        @DisplayName("Account without ACTIVE status returns false for isActive()")
        @Test
        void testIsActiveWhenStatusInactive() {
            Account inactiveAccount = new Account();
            assertFalse(inactiveAccount.isActive(), "Account should not be active when status is not ACTIVE");
        }
    }
}
