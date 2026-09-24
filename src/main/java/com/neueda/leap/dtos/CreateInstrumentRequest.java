package com.neueda.leap.dtos;

import jakarta.validation.constraints.NotNull;

public record CreateInstrumentRequest(
        @NotNull String symbol,
        @NotNull String name,
        @NotNull String assetClass,
        @NotNull String currency,
        boolean tradable) {
}
