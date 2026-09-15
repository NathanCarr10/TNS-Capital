package com.neueda.leap.dtos;

import com.neueda.leap.enums.AccountStatus;

import java.math.BigDecimal;

public record AccountResponse(
        Long id,
        String holderName,
        BigDecimal cashBalance,
        AccountStatus status,
        Long lastUpdated) {
}