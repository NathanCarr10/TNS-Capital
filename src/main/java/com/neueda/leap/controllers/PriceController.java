package com.neueda.leap.controllers;

import com.neueda.leap.exceptions.InstrumentNotFoundException;
import com.neueda.leap.exceptions.PriceUnavailableException;
import com.neueda.leap.marketdata.PriceProvider;
import com.neueda.leap.marketdata.PriceQuote;
import com.neueda.leap.repositories.InstrumentRepository;
import com.neueda.leap.utils.InputNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/prices")
@RequiredArgsConstructor
public class PriceController {
    private final InstrumentRepository instrumentRepository;
    private final PriceProvider priceProvider;

    @GetMapping("/{symbol}")
    public ResponseEntity<PriceQuote> getPrice(@PathVariable String symbol) {
        // Only known instruments, so arbitrary symbols are never sent to the market data API
        String normalizedSymbol = InputNormalizer.normalize(symbol);
        instrumentRepository.findBySymbol(normalizedSymbol)
                .orElseThrow(() -> new InstrumentNotFoundException("Instrument not found: " + symbol));
        PriceQuote quote = priceProvider.latestPrice(normalizedSymbol)
                .orElseThrow(() -> new PriceUnavailableException("No live price for " + normalizedSymbol));
        return ResponseEntity.ok(quote);
    }
}
