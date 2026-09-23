package com.neueda.leap.repositories;

import com.neueda.leap.model.Instrument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

/**
 * Spring Data JPA Repository for Instrument persistence.
 * 
 * Provides CRUD operations and custom query methods.
 * JpaRepository automatically provides: findById, save, findAll, delete, etc.
 */
@Repository
public interface InstrumentRepository extends JpaRepository<Instrument, Long> {
    /**
     * Finds an instrument by its symbol (typically normalized).
     *
     * @param symbol the instrument symbol
     * @return Optional containing the instrument if found, empty otherwise
     */
    Optional<Instrument> findBySymbol(String symbol);
}
