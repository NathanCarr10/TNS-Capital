package com.neueda.leap.model;

import java.time.Instant;
import com.neueda.leap.time.Clock;
import com.neueda.leap.utils.InputNormalizer;

import java.math.BigDecimal;
import java.util.UUID;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;
import jakarta.persistence.*;

/**
 * Order domain entity.
 * 
 * Manages order data with core validations and business logic.
 * Follows Domain-Driven Design principles.
 */
@Entity
@Table(name = "orders", uniqueConstraints = @UniqueConstraint(columnNames = "idempotencyKey"))
public class Order {
    @Id
    private UUID id;
    
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
    private Instant createdOn;

    public Order(Clock clock) {
        this.id = UUID.randomUUID();
        this.createdOn = clock.now();
    }

    public Order(Long accountId, String symbol, OrderSide side, Integer quantity, BigDecimal price,
            String idempotencyKey, Clock clock) {
        this(clock);
        validateConstructorArgs(accountId, symbol, side, quantity, price, idempotencyKey);
        this.accountId = accountId;
        this.symbol = InputNormalizer.normalize(symbol);
        this.side = side;
        this.quantity = quantity;
        this.price = new BigDecimal(price.toPlainString());
        this.status = OrderStatus.NEW;
        this.idempotencyKey = InputNormalizer.normalize(idempotencyKey);
    }

    public Order(Order other, Clock clock) {
        this(clock);
        if (other == null) {
            throw new IllegalArgumentException("Source order cannot be null");
        }
        this.id = other.id;
        this.accountId = other.accountId;
        this.symbol = other.symbol;
        this.side = other.side;
        this.quantity = other.quantity;
        this.price = new BigDecimal(other.price.toPlainString());
        this.status = other.status;
        this.idempotencyKey = other.idempotencyKey;
        this.createdOn = other.createdOn;
    }

    private void validateConstructorArgs(Long accountId, String symbol, OrderSide side, Integer quantity,
            BigDecimal price, String idempotencyKey) {
        if (accountId == null || accountId <= 0) {
            throw new IllegalArgumentException("Valid account ID is required");
        }
        if (symbol == null || InputNormalizer.normalize(symbol).isEmpty()) {
            throw new IllegalArgumentException("Symbol cannot be null or empty");
        }
        if (side == null) {
            throw new IllegalArgumentException("Order side cannot be null");
        }
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }
        if (idempotencyKey == null || InputNormalizer.normalize(idempotencyKey).isEmpty()) {
            throw new IllegalArgumentException("Idempotency key cannot be null or empty");
        }
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
        return new BigDecimal(price.toPlainString());
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

    public void setStatus(OrderStatus newStatus) {
        if (newStatus == null) {
            throw new IllegalArgumentException("Status cannot be null");
        }
        validateTransition(this.status, newStatus);
        this.status = newStatus;
    }

    /**
     * Validates state transitions are allowed.
     * Valid transitions: NEW → FILLED, NEW → REJECTED, NEW → CANCELLED
     * Terminal states: FILLED, REJECTED, CANCELLED (no further transitions)
     *
     * @param from current status
     * @param to   new status
     * @throws IllegalStateException if transition is invalid
     */
    private void validateTransition(OrderStatus from, OrderStatus to) {
        // Terminal states cannot transition to anything
        if (from == OrderStatus.FILLED || from == OrderStatus.REJECTED || from == OrderStatus.CANCELLED) {
            throw new IllegalStateException(
                    String.format("Cannot transition from terminal state %s to %s", from, to));
        }

        // From NEW state, can only go to FILLED, REJECTED or CANCELLED
        if (from == OrderStatus.NEW) {
            if (to == OrderStatus.FILLED || to == OrderStatus.REJECTED || to == OrderStatus.CANCELLED) {
                return; // Valid transition
            }
        }

        // Any other transition is invalid
        throw new IllegalStateException(
                String.format("Invalid transition from %s to %s", from, to));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        Order other = (Order) obj;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "Order{" +
                "id=" + id +
                ", accountId=" + accountId +
                ", symbol='" + symbol + '\'' +
                ", side=" + side +
                ", quantity=" + quantity +
                ", price=" + price +
                ", status=" + status +
                ", idempotencyKey='" + idempotencyKey + '\'' +
                ", createdOn=" + createdOn +
                '}';
    }
}