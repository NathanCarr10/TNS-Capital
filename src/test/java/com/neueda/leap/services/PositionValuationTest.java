package com.neueda.leap.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.neueda.leap.dtos.PositionResponse;
import com.neueda.leap.model.Instrument;
import com.neueda.leap.model.Position;
import com.neueda.leap.repositories.InstrumentRepository;

@DisplayName("PositionValuation")
class PositionValuationTest {
    @Mock
    private InstrumentRepository instrumentRepository;

    private PositionValuation valuation;

    private final Position acme = new Position(1L, "ACME", 10, new BigDecimal("20.00"));
    private final Position newco = new Position(1L, "NEWCO", 5, new BigDecimal("3.00"));

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        valuation = new PositionValuation(instrumentRepository);
    }

    private static Instrument instrument(String symbol, String price) {
        Instrument instrument = new Instrument(symbol, symbol + " Inc", "Equity", "USD", true);
        if (price != null) {
            instrument.updatePrice(new BigDecimal(price), Instant.parse("2026-10-12T09:00:00Z"));
        }
        return instrument;
    }

    @Test
    @DisplayName("values a position at quantity x last price")
    void valuesAtLastPrice() {
        when(instrumentRepository.findBySymbol("ACME")).thenReturn(Optional.of(instrument("ACME", "25.50")));

        PositionResponse response = valuation.toResponse(acme);

        assertEquals(new BigDecimal("255.00"), response.marketValue());
        assertEquals(new BigDecimal("20.00"), response.averageCost());
    }

    @Test
    @DisplayName("leaves market value null when the instrument has no price")
    void nullWithoutPrice() {
        when(instrumentRepository.findBySymbol("NEWCO")).thenReturn(Optional.of(instrument("NEWCO", null)));

        assertNull(valuation.toResponse(newco).marketValue());
    }

    @Test
    @DisplayName("prices a list of positions with one instrument lookup")
    void valuesList() {
        when(instrumentRepository.findAllById(List.of("ACME", "NEWCO")))
                .thenReturn(List.of(instrument("ACME", "25.50"), instrument("NEWCO", null)));

        List<PositionResponse> responses = valuation.toResponses(List.of(acme, newco));

        assertEquals(new BigDecimal("255.00"), responses.get(0).marketValue());
        assertNull(responses.get(1).marketValue());
        verify(instrumentRepository, never()).findBySymbol(any());
    }
}
