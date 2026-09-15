package com.neueda.leap.dtos;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.neueda.leap.enums.AccountStatus;

class AccountResponseTest {

    @Test
    void testAccountResponseCreation() {
        // ARRANGE
        Long id = 1L;
        String holderName = "John Doe";
        BigDecimal cashBalance = new BigDecimal("10000.00");
        AccountStatus status = AccountStatus.ACTIVE;
        Long timestamp = System.currentTimeMillis();
        
        // ACT
        AccountResponse response = new AccountResponse(id, holderName, cashBalance, status, timestamp);
        
        // ASSERT
        assertEquals(id, response.id());
        assertEquals(holderName, response.holderName());
        assertEquals(cashBalance, response.cashBalance());
        assertEquals(status, response.status());
        assertEquals(timestamp, response.lastUpdated());
    }

    @Test
    void testAccountResponseWithSuspendedStatus() {
        // ARRANGE
        AccountResponse response = new AccountResponse(
                2L, "Jane Smith", new BigDecimal("5000.00"), 
                AccountStatus.SUSPENDED, System.currentTimeMillis());
        
        // ACT & ASSERT
        assertEquals(AccountStatus.SUSPENDED, response.status());
    }
}
