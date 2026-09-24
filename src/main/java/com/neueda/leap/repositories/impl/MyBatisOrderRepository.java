package com.neueda.leap.repositories.impl;

import com.neueda.leap.model.Order;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.mappers.OrderMapper;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Primary;

/**
 * MyBatis implementation of OrderRepository.
 * 
 * Persists Order entities to the database via MyBatis mapper.
 */
@Repository
@Primary
public class MyBatisOrderRepository implements OrderRepository {
    private final OrderMapper orderMapper;

    public MyBatisOrderRepository(OrderMapper orderMapper) {
        this.orderMapper = Objects.requireNonNull(orderMapper);
    }

    @Override
    public Optional<Order> findByIdempotencyKey(String idempotencyKey) {
        Objects.requireNonNull(idempotencyKey, "Idempotency key cannot be null");
        Order order = orderMapper.findByIdempotencyKey(idempotencyKey);
        return Optional.ofNullable(order);
    }

    @Override
    public Optional<Order> findById(UUID id) {
        Objects.requireNonNull(id, "Order ID cannot be null");
        Order order = orderMapper.findById(id);
        return Optional.ofNullable(order);
    }

    @Override
    public <S extends Order> S save(S order) {
        Objects.requireNonNull(order, "Order cannot be null");
        orderMapper.save(order);
        return order;
    }

    @Override
    public List<Order> findByAccountId(Long accountId) {
        Objects.requireNonNull(accountId, "Account ID cannot be null");
        return new java.util.ArrayList<>(); // TODO: Implement with orderMapper query
    }

    // Stub implementations for JpaRepository methods
    @Override
    public <S extends Order> java.util.List<S> findAll(org.springframework.data.domain.Example<S> example,
                                                       org.springframework.data.domain.Sort sort) {
        return new java.util.ArrayList<>();
    }
}
