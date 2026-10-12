package com.neueda.leap.controllers;

import com.neueda.leap.exceptions.InstrumentNotFoundException;
import com.neueda.leap.exceptions.MarketDataUnavailableException;
import com.neueda.leap.exceptions.PriceUnavailableException;
import com.neueda.leap.marketdata.BarTimeframe;
import com.neueda.leap.marketdata.MarketDataService;
import com.neueda.leap.marketdata.MarketQuote;
import com.neueda.leap.marketdata.PriceBar;
import com.neueda.leap.marketdata.PriceProvider;
import com.neueda.leap.marketdata.PriceQuote;
import com.neueda.leap.repositories.InstrumentRepository;
import com.neueda.leap.utils.InputNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/prices")
@RequiredArgsConstructor
public class PriceController {
    static final int MAX_SYMBOLS = 50;
    static final int MAX_BARS = 1000;

    private final InstrumentRepository instrumentRepository;
    private final PriceProvider priceProvider;
    private final MarketDataService marketDataService;

    /**
     * Quotes with daily change for a watchlist, e.g. ?symbols=AAPL,MSFT.
     * A symbol with no live price is still returned, with null price fields.
     */
    @GetMapping
    public ResponseEntity<List<MarketQuote>> getQuotes(@RequestParam List<String> symbols) {
        List<String> normalizedSymbols = symbols.stream()
                .map(InputNormalizer::normalize)
                .filter(symbol -> !symbol.isEmpty())
                .distinct()
                .toList();
        if (normalizedSymbols.isEmpty() || normalizedSymbols.size() > MAX_SYMBOLS) {
            throw new IllegalArgumentException("Between 1 and " + MAX_SYMBOLS + " symbols are allowed");
        }
        normalizedSymbols.forEach(this::requireInstrument);
        return ResponseEntity.ok(marketDataService.quotes(normalizedSymbols));
    }

    @GetMapping("/{symbol}")
    public ResponseEntity<PriceQuote> getPrice(@PathVariable String symbol) {
        String normalizedSymbol = requireInstrument(InputNormalizer.normalize(symbol));
        PriceQuote quote = priceProvider.latestPrice(normalizedSymbol)
                .orElseThrow(() -> new PriceUnavailableException("No live price for " + normalizedSymbol));
        return ResponseEntity.ok(quote);
    }

    /**
     * The most recent OHLC bars of an instrument, oldest first, for a candlestick chart.
     *
     * @param timeframe 15Min, 1Hour or 1Day
     * @param limit     how many bars, 1 to MAX_BARS
     */
    @GetMapping("/{symbol}/bars")
    public ResponseEntity<List<PriceBar>> getBars(@PathVariable String symbol,
            @RequestParam(defaultValue = "1Day") String timeframe,
            @RequestParam(defaultValue = "100") int limit) {
        if (limit < 1 || limit > MAX_BARS) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_BARS);
        }
        BarTimeframe barTimeframe = BarTimeframe.fromValue(timeframe);
        String normalizedSymbol = requireInstrument(InputNormalizer.normalize(symbol));
        List<PriceBar> bars = marketDataService.bars(normalizedSymbol, barTimeframe, limit);
        if (bars.isEmpty()) {
            throw new MarketDataUnavailableException("No " + timeframe + " bars for " + normalizedSymbol);
        }
        return ResponseEntity.ok(bars);
    }

    // Only known instruments, so arbitrary symbols are never sent to the market data API
    private String requireInstrument(String normalizedSymbol) {
        instrumentRepository.findBySymbol(normalizedSymbol)
                .orElseThrow(() -> new InstrumentNotFoundException("Instrument not found: " + normalizedSymbol));
        return normalizedSymbol;
    }
}
