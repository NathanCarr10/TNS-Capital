package com.neueda.leap.repositories;

import com.neueda.leap.model.Position;
import java.util.Optional;

/**
 * Repository abstraction for Position persistence.
 * 
 * Manages positions keyed by (accountId, symbol).
 * Decouples business logic from storage implementation.
 */
public interface PositionRepository {
    /**
     * Finds a position for an account and symbol.
     *
     * @param accountId the account ID
     * @param symbol    the instrument symbol (typically normalized)
     * @return Optional containing the position if found, empty otherwise
     */
    Optional<Position> findPosition(Long accountId, String symbol);

    /**
     * Saves or updates a position.
     *
     * @param position the position to persist
     * @throws IllegalArgumentException if position is null
     */
    void save(Position position);

    /**
     * Deletes a position by account ID and symbol.
     *
     * @param accountId the account ID
     * @param symbol    the instrument symbol
     */
    void delete(Long accountId, String symbol);

    /**
     * Finds all positions for a given account.
     *
     * @param accountId the account ID
     * @return list of positions for the account; empty list if none found
     */
    java.util.List<Position> findByAccountId(Long accountId);
}
