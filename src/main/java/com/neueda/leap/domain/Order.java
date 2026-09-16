package com.neueda.leap.domain;

import java.time.Instant;
import com.neueda.leap.time.SystemClock;
import java.math.BigDecimal;
import java.util.UUID;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.time.Clock;

/**
 * Order domain entity.
 * 
 * Manages order data with core validations and business logic.
 * Follows Domain-Driven Design principles.
 */
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

    public Order(Clock clock) {
        this.id = UUID.randomUUID();
        this.createdOn = clock.now();
    }

    public Order(Long accountId, String symbol, OrderSide side, Integer quantity, BigDecimal price,
            String idempotencyKey, Clock clock) {
        this(clock);
        validateConstructorArgs(accountId, symbol, side, quantity, price, idempotencyKey);
        this.accountId = accountId;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.price = new BigDecimal(price.toPlainString());
        this.status = OrderStatus.NEW;
        this.idempotencyKey = idempotencyKey;
    }

    public Order(Order other) {
        this(SystemClock.INSTANCE);
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
        if (symbol == null || symbol.trim().isEmpty()) {
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
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
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

    public void setStatus(OrderStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Status cannot be null");
        }
        this.status = status;
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