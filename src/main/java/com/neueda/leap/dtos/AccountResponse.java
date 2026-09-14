package com.neueda.leap.dtos;

import com.neueda.leap.domain.enums.AccountStatus;

import java.math.BigDecimal;

public record AccountResponse(
                Long id,
                String holderName,
                BigDecimal cashBalance,
                AccountStatus status,
                Long lastUpdated) {
}