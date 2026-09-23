package com.neueda.leap.repositories;

import com.neueda.leap.model.Instrument;

import java.util.List;
import java.util.Optional;

/**
 * Repository abstraction for Instrument persistence.
 * 
 * Instruments are typically read-only after creation.
 * Decouples business logic from storage implementation.
 */
public interface InstrumentRepository {
    /**
     * Finds an instrument by symbol.
     *
     * @param symbol the instrument symbol (typically normalized)
     * @return Optional containing the instrument if found, empty otherwise
     */
    Optional<Instrument> findBySymbol(String symbol);

    /**
     * Finds all instruments in the system.
     *
     * @return list of all instruments; empty list if none found
     */
    List<Instrument> findAll();

    /**
     * Saves or updates an instrument.
     *
     * @param instrument the instrument to persist
     * @throws IllegalArgumentException if instrument is null
     */
    void save(Instrument instrument);
}
