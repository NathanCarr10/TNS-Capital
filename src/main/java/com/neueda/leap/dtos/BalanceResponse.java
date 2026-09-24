package com.neueda.leap.dtos;

import java.math.BigDecimal;

public record BalanceResponse(
        BigDecimal cashBalance) {
}
