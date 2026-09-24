package com.neueda.leap.repositories.impl;

import com.neueda.leap.model.Position;
import com.neueda.leap.repositories.PositionRepository;
import com.neueda.leap.mappers.PositionMapper;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Primary;

/**
 * MyBatis implementation of PositionRepository.
 * 
 * Persists Position entities to the database via MyBatis mapper.
 */
@Repository
@Primary
public class MyBatisPositionRepository implements PositionRepository {
    private final PositionMapper positionMapper;

    public MyBatisPositionRepository(PositionMapper positionMapper) {
        this.positionMapper = Objects.requireNonNull(positionMapper);
    }

    @Override
    public Optional<Position> findByAccountIdAndSymbol(Long accountId, String symbol) {
        Objects.requireNonNull(accountId, "Account ID cannot be null");
        Objects.requireNonNull(symbol, "Symbol cannot be null");
        Position position = positionMapper.findPosition(accountId, symbol);
        return Optional.ofNullable(position);
    }

    @Override
    public <S extends Position> S save(S position) {
        Objects.requireNonNull(position, "Position cannot be null");
        positionMapper.save(position);
        return position;
    }

    @Override
    public void deleteByAccountIdAndSymbol(Long accountId, String symbol) {
        Objects.requireNonNull(accountId, "Account ID cannot be null");
        Objects.requireNonNull(symbol, "Symbol cannot be null");
        positionMapper.delete(accountId, symbol);
    }

    @Override
    public List<Position> findByAccountId(Long accountId) {
        return new java.util.ArrayList<>(); // TODO: Implement with positionMapper
    }

    // Stub implementations for JpaRepository methods
    @Override
    public <S extends Position> java.util.List<S> findAll(org.springframework.data.domain.Example<S> example,
                                                          org.springframework.data.domain.Sort sort) {
        return new java.util.ArrayList<>();
    }
}
