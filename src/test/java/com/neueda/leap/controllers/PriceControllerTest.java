package com.neueda.leap.controllers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.neueda.leap.exceptions.InstrumentNotFoundException;
import com.neueda.leap.exceptions.MarketDataUnavailableException;
import com.neueda.leap.marketdata.BarTimeframe;
import com.neueda.leap.marketdata.MarketDataService;
import com.neueda.leap.marketdata.MarketQuote;
import com.neueda.leap.marketdata.PriceBar;
import com.neueda.leap.marketdata.PriceProvider;
import com.neueda.leap.model.Instrument;
import com.neueda.leap.repositories.InstrumentRepository;

@DisplayName("PriceController")
class PriceControllerTest {
    private InstrumentRepository instrumentRepository;
    private MarketDataService marketDataService;
    private PriceController controller;

    @BeforeEach
    void setUp() {
        instrumentRepository = mock(InstrumentRepository.class);
        marketDataService = mock(MarketDataService.class);
        when(instrumentRepository.findBySymbol(anyString())).thenReturn(Optional.empty());
        when(instrumentRepository.findBySymbol("AAPL")).thenReturn(Optional.of(mock(Instrument.class)));
        when(instrumentRepository.findBySymbol("MSFT")).thenReturn(Optional.of(mock(Instrument.class)));
        controller = new PriceController(instrumentRepository, mock(PriceProvider.class), marketDataService);
    }

    @Test
    @DisplayName("Quotes normalized, de-duplicated symbols")
    void quotesNormalizedSymbols() {
        List<MarketQuote> quotes = List.of(new MarketQuote("AAPL", BigDecimal.TEN, null, null, null, Instant.EPOCH));
        when(marketDataService.quotes(List.of("AAPL", "MSFT"))).thenReturn(quotes);

        assertEquals(quotes, controller.getQuotes(List.of(" aapl", "MSFT", "AAPL")).getBody());
    }

    @Test
    @DisplayName("Rejects a quote request with an unknown symbol")
    void rejectsUnknownSymbol() {
        assertThrows(InstrumentNotFoundException.class, () -> controller.getQuotes(List.of("AAPL", "NOPE")));
        verifyNoInteractions(marketDataService);
    }

    @Test
    @DisplayName("Rejects a quote request with no symbols or too many")
    void rejectsSymbolCount() {
        assertThrows(IllegalArgumentException.class, () -> controller.getQuotes(List.of(" ")));
        List<String> tooMany = Collections.nCopies(PriceController.MAX_SYMBOLS + 1, "X")
                .stream().map(s -> s + Math.random()).toList();
        assertThrows(IllegalArgumentException.class, () -> controller.getQuotes(tooMany));
        verifyNoInteractions(marketDataService);
    }

    @Test
    @DisplayName("Returns the bars of a known instrument")
    void returnsBars() {
        List<PriceBar> bars = List.of(new PriceBar(Instant.EPOCH, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, 1));
        when(marketDataService.bars("AAPL", BarTimeframe.MINUTES_15, 20)).thenReturn(bars);

        assertEquals(bars, controller.getBars("aapl", "15Min", 20).getBody());
    }

    @Test
    @DisplayName("Rejects an unsupported timeframe or an out-of-range limit")
    void rejectsBadBarParameters() {
        assertThrows(IllegalArgumentException.class, () -> controller.getBars("AAPL", "1Week", 100));
        assertThrows(IllegalArgumentException.class, () -> controller.getBars("AAPL", "1Day", 0));
        assertThrows(IllegalArgumentException.class,
                () -> controller.getBars("AAPL", "1Day", PriceController.MAX_BARS + 1));
        verifyNoInteractions(marketDataService);
    }

    @Test
    @DisplayName("Reports market data unavailable when there are no bars")
    void reportsNoBars() {
        when(marketDataService.bars(any(), any(), anyInt())).thenReturn(List.of());

        assertThrows(MarketDataUnavailableException.class, () -> controller.getBars("AAPL", "1Day", 100));
    }
}
