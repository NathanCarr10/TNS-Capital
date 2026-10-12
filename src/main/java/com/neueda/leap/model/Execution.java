package com.neueda.leap.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The fill for a FILLED order (table executions): what the broker actually
 * traded, at what price, where and when. Orders are filled in full, so there
 * is one execution per filled order.
 */
@Entity
@Table(name = "executions")
public class Execution {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID orderId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private String venue;

    @Column(nullable = false)
    private Instant executedOn;

    protected Execution() {
        // JPA no-arg constructor
    }

    public Execution(UUID id, UUID orderId, Integer quantity, BigDecimal price, String venue, Instant executedOn) {
        this.id = Objects.requireNonNull(id);
        this.orderId = Objects.requireNonNull(orderId);
        this.quantity = Objects.requireNonNull(quantity);
        this.price = Objects.requireNonNull(price);
        this.venue = Objects.requireNonNull(venue);
        this.executedOn = Objects.requireNonNull(executedOn);
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getVenue() {
        return venue;
    }

    public Instant getExecutedOn() {
        return executedOn;
    }
}
