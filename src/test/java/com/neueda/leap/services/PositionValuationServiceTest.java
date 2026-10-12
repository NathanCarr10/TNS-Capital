package com.neueda.leap.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.neueda.leap.dtos.PositionResponse;
import com.neueda.leap.marketdata.PriceProvider;
import com.neueda.leap.marketdata.PriceQuote;
import com.neueda.leap.model.Position;

@DisplayName("PositionValuationService")
class PositionValuationServiceTest {
    private PriceProvider priceProvider;
    private PositionValuationService service;

    @BeforeEach
    void setUp() {
        priceProvider = mock(PriceProvider.class);
        service = new PositionValuationService(priceProvider);
    }

    @Test
    @DisplayName("Values each position at the latest price, pricing all symbols at once")
    void valuesAtLatestPrice() {
        when(priceProvider.latestPrices(List.of("AAPL", "MSFT"))).thenReturn(Map.of(
                "AAPL", new PriceQuote("AAPL", new BigDecimal("227.31"), Instant.EPOCH)));

        List<PositionResponse> responses = service.toResponses(List.of(
                new Position(1L, "AAPL", 10, new BigDecimal("200.00")),
                new Position(1L, "MSFT", 5, new BigDecimal("400.00"))));

        verify(priceProvider, times(1)).latestPrices(any());
        assertEquals(new PositionResponse(1L, "AAPL", 10, new BigDecimal("200.00"), new BigDecimal("2273.10")),
                responses.get(0));
        // No live price: market value is unknown, not zero
        assertNull(responses.get(1).marketValue());
    }

    @Test
    @DisplayName("Makes no price lookup for an empty portfolio")
    void noLookupForNoPositions() {
        assertTrue(service.toResponses(List.of()).isEmpty());
        verifyNoInteractions(priceProvider);
    }
}
