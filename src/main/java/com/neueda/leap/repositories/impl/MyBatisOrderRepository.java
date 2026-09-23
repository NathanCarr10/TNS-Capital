package com.neueda.leap.repositories.impl;

import com.neueda.leap.model.Order;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.mappers.OrderMapper;
import java.util.Optional;
import java.util.Objects;
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
    public void save(Order order) {
        Objects.requireNonNull(order, "Order cannot be null");
        orderMapper.save(order);
    }
}
