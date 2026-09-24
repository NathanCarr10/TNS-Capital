package com.neueda.leap.repositories.impl;

import com.neueda.leap.model.Instrument;
import com.neueda.leap.repositories.InstrumentRepository;
import com.neueda.leap.mappers.InstrumentMapper;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Primary;

/**
 * MyBatis implementation of InstrumentRepository.
 * 
 * Persists Instrument entities to the database via MyBatis mapper.
 */
@Repository
@Primary
public class MyBatisInstrumentRepository implements InstrumentRepository {
    private final InstrumentMapper instrumentMapper;

    public MyBatisInstrumentRepository(InstrumentMapper instrumentMapper) {
        this.instrumentMapper = Objects.requireNonNull(instrumentMapper);
    }

    @Override
    public Optional<Instrument> findBySymbol(String symbol) {
        Objects.requireNonNull(symbol, "Symbol cannot be null");
        Instrument instrument = instrumentMapper.findBySymbol(symbol);
        return Optional.ofNullable(instrument);
    }

    @Override
    public <S extends Instrument> S save(S instrument) {
        Objects.requireNonNull(instrument, "Instrument cannot be null");
        if (instrument.getId() != null) {
            // Update existing
            instrumentMapper.save(instrument);
        } else {
            // Insert new
            instrumentMapper.save(instrument);
        }
        return instrument;
    }

    // Stub implementations for JpaRepository methods
    @Override
    public <S extends Instrument> List<S> findAll(org.springframework.data.domain.Example<S> example,
                                                  org.springframework.data.domain.Sort sort) {
        return new java.util.ArrayList<>();
    }
}
