package com.neueda.leap.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.neueda.leap.dtos.PositionResponse;
import com.neueda.leap.marketdata.PriceProvider;
import com.neueda.leap.marketdata.PriceQuote;
import com.neueda.leap.model.Position;

@DisplayName("PositionValuation")
class PositionValuationTest {
    private final PriceProvider priceProvider = mock(PriceProvider.class);
    private final PositionValuation valuation = new PositionValuation(priceProvider);

    private final Position aapl = new Position(1L, "AAPL", 10, new BigDecimal("200.00"));
    private final Position tlt = new Position(1L, "TLT", 5, new BigDecimal("90.00"));

    private static Optional<PriceQuote> quote(String symbol, String price) {
        return Optional.of(new PriceQuote(symbol, new BigDecimal(price), Instant.parse("2026-10-12T14:30:00Z")));
    }

    @Test
    @DisplayName("values a position at quantity x live price, to the cent")
    void valuesAtLivePrice() {
        when(priceProvider.latestPrice("AAPL")).thenReturn(quote("AAPL", "227.4651"));

        PositionResponse response = valuation.toResponse(aapl);

        assertEquals(new BigDecimal("2274.65"), response.marketValue());
        assertEquals(new BigDecimal("200.00"), response.averageCost());
    }

    @Test
    @DisplayName("leaves market value null when there is no live price")
    void nullWithoutPrice() {
        when(priceProvider.latestPrice("TLT")).thenReturn(Optional.empty());

        assertNull(valuation.toResponse(tlt).marketValue());
    }

    @Test
    @DisplayName("prices each position in a list")
    void valuesList() {
        when(priceProvider.latestPrice("AAPL")).thenReturn(quote("AAPL", "230.00"));
        when(priceProvider.latestPrice("TLT")).thenReturn(Optional.empty());

        List<PositionResponse> responses = valuation.toResponses(List.of(aapl, tlt));

        assertEquals(new BigDecimal("2300.00"), responses.get(0).marketValue());
        assertNull(responses.get(1).marketValue());
    }
}
