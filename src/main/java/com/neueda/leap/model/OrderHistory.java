package com.neueda.leap.model;

import java.time.Instant;
import com.neueda.leap.time.Clock;
import java.math.BigDecimal;
import java.util.UUID;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;
import jakarta.persistence.*;

/**
 * OrderHistory entity for audit trail of deleted orders.
 * 
 * Preserves order data even after the order is deleted from the active orders table.
 * Enables historical queries and compliance auditing.
 */
@Entity
@Table(name = "order_history")
public class OrderHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private UUID orderId;
    
    @Column(nullable = false)
    private Long accountId;
    
    @Column(nullable = false)
    private String symbol;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderSide side;
    
    @Column(nullable = false)
    private Integer quantity;
    
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;
    
    @Column(nullable = false, unique = true)
    private String idempotencyKey;
    
    @Column(nullable = false)
    private Instant orderCreatedOn;
    
    @Column(nullable = false)
    private Instant deletedOn;

    protected OrderHistory() {
        // JPA no-arg constructor
    }

    /**
     * Creates an order history record from an order being deleted.
     * 
     * @param order the order to archive
     * @param clock the clock for timestamp
     */
    public OrderHistory(Order order, Clock clock) {
        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }
        this.orderId = order.getId();
        this.accountId = order.getAccountId();
        this.symbol = order.getSymbol();
        this.side = order.getSide();
        this.quantity = order.getQuantity();
        this.price = new BigDecimal(order.getPrice().toPlainString());
        this.status = order.getStatus();
        this.idempotencyKey = order.getIdempotencyKey();
        this.orderCreatedOn = order.getCreatedOn();
        this.deletedOn = clock.now();
    }

    // Getters
    public Long getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
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
        return new BigDecimal(price.toPlainString());
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getOrderCreatedOn() {
        return orderCreatedOn;
    }

    public Instant getDeletedOn() {
        return deletedOn;
    }
}
