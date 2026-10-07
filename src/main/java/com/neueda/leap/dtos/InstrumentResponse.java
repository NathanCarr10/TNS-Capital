package com.neueda.leap.dtos;

public record InstrumentResponse(
        String symbol,
        String name,
        String assetClass,
        String currency,
        boolean tradable) {
}
