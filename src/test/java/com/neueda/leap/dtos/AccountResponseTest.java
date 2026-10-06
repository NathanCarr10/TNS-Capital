package com.neueda.leap.dtos;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.neueda.leap.enums.AccountStatus;

class AccountResponseTest {

    @Test
    void testAccountResponseCreation() {
        Long id = 1L;
        String accountNumber = "ACC123456";
        String holderName = "John Doe";
        BigDecimal cashBalance = new BigDecimal("10000.00");
        AccountStatus status = AccountStatus.ACTIVE;
        Long timestamp = System.currentTimeMillis();
        AccountResponse response = new AccountResponse(id, accountNumber, holderName, cashBalance, status, timestamp, "john");
        assertEquals(id, response.id());
        assertEquals(accountNumber, response.accountNumber());
        assertEquals(holderName, response.holderName());
        assertEquals(cashBalance, response.cashBalance());
        assertEquals(status, response.status());
        assertEquals(timestamp, response.lastUpdated());
    }

    @Test
    void testAccountResponseWithSuspendedStatus() {
        AccountResponse response = new AccountResponse(
                2L, "ACC654321", "Jane Smith", new BigDecimal("5000.00"),
                AccountStatus.SUSPENDED, System.currentTimeMillis(), "jane");
        assertEquals("ACC654321", response.accountNumber());
        assertEquals(AccountStatus.SUSPENDED, response.status());
    }
}
