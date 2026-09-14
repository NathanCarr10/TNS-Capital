package com.neueda.leap.dtos;

import java.math.BigDecimal;

public record PositionResponse(
                Long accountId,
                String symbol,
                Integer quantity,
                BigDecimal averageCost,
                BigDecimal marketValue) {
}