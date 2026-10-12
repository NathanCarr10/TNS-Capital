package com.neueda.leap.dtos;

import java.math.BigDecimal;

/**
 * @param marketValue quantity at the latest live price, or null when no live price is available
 */
public record PositionResponse(
                Long accountId,
                String symbol,
                Integer quantity,
                BigDecimal averageCost,
                BigDecimal marketValue) {
}
