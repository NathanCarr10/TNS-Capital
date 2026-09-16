package com.neueda.leap.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.neueda.leap.enums.AccountStatus;
import com.neueda.leap.exceptions.InsufficientFundsException;
import com.neueda.leap.time.ClockTest;

class AccountTest {
    private Account account;
    private ClockTest testClock;

    @BeforeEach
    void setUp() {
        testClock = new ClockTest(Instant.parse("2026-09-16T10:00:00Z"));
        account = new Account("ACC001", "John Doe", new BigDecimal("10000.00"), testClock);
    }

    @Test
    void testAccountConstructor() {
        // ARRANGE: account created in setUp

        // ACT & ASSERT: verify initialization
        assertEquals("ACC001", account.getAccountId());
        assertEquals("John Doe", account.getHolderName());
        assertEquals(new BigDecimal("10000.00"), account.getCashBalance());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
        assertEquals(0, account.getVersion());
        assertNotNull(account.getLastUpdated());
    }

    @Test
    void testDebitSuccessful() throws InsufficientFundsException {
        // ARRANGE: account created in setUp with 10000.00

        // ACT
        account.debit(new BigDecimal("1000.00"));

        // ASSERT
        assertEquals(new BigDecimal("9000.00"), account.getCashBalance());
    }

    @Test
    void testDebitInsufficientFunds() {
        // ARRANGE: account created in setUp with 10000.00

        // ACT & ASSERT: expect exception
        assertThrows(InsufficientFundsException.class, () -> {
            account.debit(new BigDecimal("15000.00"));
        });
    }

    @Test
    void testDebitExactAmount() throws InsufficientFundsException {
        // ARRANGE: account created in setUp with 10000.00

        // ACT
        account.debit(new BigDecimal("10000.00"));

        // ASSERT
        assertEquals(new BigDecimal("0.00"), account.getCashBalance());
    }

    @Test
    void testCredit() {
        // ARRANGE: account created in setUp with 10000.00

        // ACT
        account.credit(new BigDecimal("5000.00"));

        // ASSERT
        assertEquals(new BigDecimal("15000.00"), account.getCashBalance());
    }

    @Test
    void testMultipleDebitsAndCredits() throws InsufficientFundsException {
        // ARRANGE: account created in setUp with 10000.00

        // ACT
        account.debit(new BigDecimal("2000.00"));
        account.credit(new BigDecimal("3000.00"));
        account.debit(new BigDecimal("1500.00"));

        // ASSERT
        assertEquals(new BigDecimal("9500.00"), account.getCashBalance());
    }

    @Test
    void testIsActiveWhenStatusActive() {
        // ARRANGE: account created in setUp with ACTIVE status

        // ACT & ASSERT
        assertTrue(account.isActive());
    }

    @Test
    void testIsActiveWhenStatusInactive() {
        // ARRANGE: create account with no status
        Account inactiveAccount = new Account();

        // ACT & ASSERT: should not be active
        assertFalse(inactiveAccount.isActive());
    }
}
