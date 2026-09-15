package com.neueda.leap.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;

public class Order {
    private UUID id;
    private Long accountId;
    private String symbol;
    private OrderSide side;
    private Integer quantity;
    private BigDecimal price;
    private OrderStatus status;
    private String idempotencyKey;
    private Instant createdOn;

    public Order() {
        this.id = UUID.randomUUID();
        this.createdOn = Instant.now();
    }

    public Order(Long accountId, String symbol, OrderSide side, Integer quantity, BigDecimal price,
            String idempotencyKey) {
        this();
        this.accountId = accountId;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.status = OrderStatus.NEW;
        this.idempotencyKey = idempotencyKey;
    }

    // Getters
    public UUID getId() {
        return id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getSymbol() {
        return symbol;
    }

    public OrderSide getSide() {
        return side;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedOn() {
        return createdOn;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }
}