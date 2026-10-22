package com.neueda.leap.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import com.neueda.leap.dtos.CreateInstrumentRequest;
import com.neueda.leap.dtos.InstrumentResponse;
import com.neueda.leap.exceptions.DuplicateInstrumentException;
import com.neueda.leap.model.Instrument;
import com.neueda.leap.repositories.InstrumentRepository;
import com.neueda.leap.utils.InputNormalizer;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/instruments")
public class InstrumentController {
    private final InstrumentRepository instrumentRepository;

    public InstrumentController(InstrumentRepository instrumentRepository) {
        this.instrumentRepository = instrumentRepository;
    }

    @Operation(summary = "List instruments")
    @GetMapping
    public ResponseEntity<List<InstrumentResponse>> getAllInstruments() {
        // Queries all instruments; enables bulk retrieval and filtering by trading status
        List<Instrument> instruments = instrumentRepository.findAll();
        List<InstrumentResponse> responses = instruments.stream()
                .map(this::mapToResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "Get an instrument")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "404", description = "INS-404 Instrument not found")
    @GetMapping("/{symbol}")
    public ResponseEntity<InstrumentResponse> getInstrument(@PathVariable String symbol) {
        // Retrieves instrument by symbol; normalizes for consistent lookup
        String normalizedSymbol = InputNormalizer.normalize(symbol);
        Instrument instrument = instrumentRepository.findBySymbol(normalizedSymbol)
                .orElseThrow(() -> new com.neueda.leap.exceptions.InstrumentNotFoundException("Instrument not found: " + symbol));
        return ResponseEntity.ok(mapToResponse(instrument));
    }
    /** Adding instruments is a support task, like opening accounts. */
    @Operation(summary = "Add an instrument", description = "Support only.")
    @ApiResponse(responseCode = "201", description = "Instrument added")
    @ApiResponse(responseCode = "403", description = "AUTH-403 Support only")
    @ApiResponse(responseCode = "409", description = "INS-409 The symbol is taken")
    @PostMapping
    @PreAuthorize("hasRole('SUPPORT')")
    public ResponseEntity<InstrumentResponse> createInstrument(@Valid @RequestBody CreateInstrumentRequest request) {
        // symbol is the primary key, so save() would overwrite an existing instrument;
        // reject duplicates explicitly instead
        Instrument instrument = new Instrument(
                request.symbol(),
                request.name(),
                request.assetClass(),
                request.currency(),
                request.tradable()
        );

        if (instrumentRepository.existsById(instrument.getSymbol())) {
            throw new DuplicateInstrumentException("Instrument already exists: " + instrument.getSymbol());
        }

        instrumentRepository.save(instrument);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapToResponse(instrument));
    }

    /** Only support may change an instrument, e.g. stop it being tradable. */
    @Operation(summary = "Change an instrument", description = "Support only, e.g. to stop trading it (tradable false).")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "403", description = "AUTH-403 Support only")
    @ApiResponse(responseCode = "404", description = "INS-404 Instrument not found")
    @PutMapping("/{symbol}")
    @PreAuthorize("hasRole('SUPPORT')")
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
                instrument.getSymbol(),
                instrument.getName(),
                instrument.getAssetClass(),
                instrument.getCurrency(),
                instrument.isTradable()
        );
    }
}
