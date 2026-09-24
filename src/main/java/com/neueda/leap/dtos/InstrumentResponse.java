package com.neueda.leap.dtos;

public record InstrumentResponse(
        Long id,
        String symbol,
        String name,
        String assetClass,
        String currency,
        boolean tradable) {
}
