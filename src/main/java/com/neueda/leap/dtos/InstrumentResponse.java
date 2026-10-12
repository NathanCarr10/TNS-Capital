package com.neueda.leap.dtos;

import java.math.BigDecimal;
import java.time.Instant;

public record InstrumentResponse(
        String symbol,
        String name,
        String assetClass,
        String currency,
        boolean tradable,
        // Latest market price; null until the price feed has sent one
        BigDecimal lastPrice,
        Instant lastPriceOn) {
}
