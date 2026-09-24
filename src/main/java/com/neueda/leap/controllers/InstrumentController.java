package com.neueda.leap.controllers;

import com.neueda.leap.dtos.CreateInstrumentRequest;
import com.neueda.leap.dtos.InstrumentResponse;
import com.neueda.leap.model.Instrument;
import com.neueda.leap.repositories.InstrumentRepository;
import com.neueda.leap.utils.InputNormalizer;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/instruments")
public class InstrumentController {
    private final InstrumentRepository instrumentRepository;

    public InstrumentController(InstrumentRepository instrumentRepository) {
        this.instrumentRepository = instrumentRepository;
    }

    @GetMapping
    public ResponseEntity<List<InstrumentResponse>> getAllInstruments() {
        // Queries all instruments; enables bulk retrieval and filtering by trading status
        List<Instrument> instruments = instrumentRepository.findAll();
        List<InstrumentResponse> responses = instruments.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{symbol}")
    public ResponseEntity<InstrumentResponse> getInstrument(@PathVariable String symbol) {
        // Retrieves instrument by symbol; normalizes for consistent lookup
        String normalizedSymbol = InputNormalizer.normalize(symbol);
        Instrument instrument = instrumentRepository.findBySymbol(normalizedSymbol)
                .orElseThrow(() -> new com.neueda.leap.exceptions.InstrumentNotFoundException("Instrument not found: " + symbol));
        return ResponseEntity.ok(mapToResponse(instrument));
    }
    @PostMapping
    public ResponseEntity<InstrumentResponse> createInstrument(@Valid @RequestBody CreateInstrumentRequest request) {
        // Creates new instrument with auto-generated ID; validates symbol not duplicate via repository constraint
        Instrument instrument = new Instrument();
        instrument.setId(System.nanoTime()); // Simple ID generation; replace with proper sequence in production
        instrument.setSymbol(InputNormalizer.normalize(request.symbol()));
        instrument.setName(request.name());
        instrument.setAssetClass(request.assetClass());
        instrument.setCurrency(request.currency());
        instrument.setTradable(request.tradable());
        
        instrumentRepository.save(instrument);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapToResponse(instrument));
    }

    @PutMapping("/{symbol}")
    public ResponseEntity<InstrumentResponse> updateInstrument(@PathVariable String symbol,
                                                            @Valid @RequestBody CreateInstrumentRequest request) {
        // Updates existing instrument; throws 404 if not found to maintain REST semantics
        String normalizedSymbol = InputNormalizer.normalize(symbol);
        Instrument instrument = instrumentRepository.findBySymbol(normalizedSymbol)
                .orElseThrow(() -> new com.neueda.leap.exceptions.InstrumentNotFoundException("Instrument not found: " + symbol));
        
        instrument.setName(request.name());
        instrument.setAssetClass(request.assetClass());
        instrument.setCurrency(request.currency());
        instrument.setTradable(request.tradable());
        
        instrumentRepository.save(instrument);
        return ResponseEntity.ok(mapToResponse(instrument));
    }

    private InstrumentResponse mapToResponse(Instrument instrument) {
        // Converts Instrument entity to response; simple pass-through for immutable reference data
        return new InstrumentResponse(
                instrument.getId(),
                instrument.getSymbol(),
                instrument.getName(),
                instrument.getAssetClass(),
                instrument.getCurrency(),
                instrument.isTradable()
        );
    }
}
