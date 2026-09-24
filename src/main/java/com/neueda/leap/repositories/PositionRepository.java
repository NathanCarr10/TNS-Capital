package com.neueda.leap.repositories;

import com.neueda.leap.model.Position;
import com.neueda.leap.model.PositionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

/**
 * Spring Data JPA Repository for Position persistence.
 * 
 * Provides CRUD operations for positions keyed by (accountId, symbol).
 * JpaRepository automatically provides: findById, save, delete, etc.
 */
@Repository
public interface PositionRepository extends JpaRepository<Position, PositionId> {
    /**
     * Finds a position for an account and symbol.
     *
     * @param accountId the account ID
     * @param symbol    the instrument symbol (typically normalized)
     * @return Optional containing the position if found, empty otherwise
     */
    Optional<Position> findByAccountIdAndSymbol(Long accountId, String symbol);

    /**
     * Deletes a position by account ID and symbol.
     *
     * @param accountId the account ID
     * @param symbol    the instrument symbol
     */
    void deleteByAccountIdAndSymbol(Long accountId, String symbol);

    /**
     * Finds all positions for a given account.
     *
     * @param accountId the account ID
     * @return list of positions for the account; empty list if none found
     */
    List<Position> findByAccountId(Long accountId);

}
