package com.neueda.leap.repositories.impl;

import com.neueda.leap.model.Position;
import com.neueda.leap.repositories.PositionRepository;
import com.neueda.leap.mappers.PositionMapper;
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
    public Optional<Position> findPosition(Long accountId, String symbol) {
        Objects.requireNonNull(accountId, "Account ID cannot be null");
        Objects.requireNonNull(symbol, "Symbol cannot be null");
        Position position = positionMapper.findPosition(accountId, symbol);
        return Optional.ofNullable(position);
    }

    @Override
    public void save(Position position) {
        Objects.requireNonNull(position, "Position cannot be null");
        positionMapper.save(position);
    }

    @Override
    public void delete(Long accountId, String symbol) {
        Objects.requireNonNull(accountId, "Account ID cannot be null");
        Objects.requireNonNull(symbol, "Symbol cannot be null");
        positionMapper.delete(accountId, symbol);
    }
}
