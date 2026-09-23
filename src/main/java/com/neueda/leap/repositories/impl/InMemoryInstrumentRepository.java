package com.neueda.leap.repositories.impl;

import com.neueda.leap.model.Instrument;
import com.neueda.leap.repositories.InstrumentRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * In-memory implementation of InstrumentRepository.
 * 
 * Wraps a Map<String, Instrument> for storage (keyed by symbol).
 * Suitable for testing and non-persistent use; replace with Spring Data JPA
 * implementation for production.
 */
public class InMemoryInstrumentRepository implements InstrumentRepository {
    private final Map<String, Instrument> storage;

    public InMemoryInstrumentRepository(Map<String, Instrument> storage) {
        this.storage = Objects.requireNonNull(storage, "Storage map cannot be null");
    }

    @Override
    public Optional<Instrument> findBySymbol(String symbol) {
        return Optional.ofNullable(storage.get(symbol));
    }

    @Override
    public List<Instrument> findAll() {
        // Returns all instruments as a new list; decouples internal storage from external API consumers
        return new ArrayList<>(storage.values());
    }
}
