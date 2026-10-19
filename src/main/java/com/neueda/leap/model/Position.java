package com.neueda.leap.model;

import java.math.BigDecimal;

import com.neueda.leap.exceptions.InsufficientHoldingsException;
import com.neueda.leap.utils.InputNormalizer;
import jakarta.persistence.*;

/**
 * Position domain entity.
 * 
 * Manages position data with core validations and business logic.
 * Follows Domain-Driven Design principles.
 */
@Entity
@Table(name = "positions")
@IdClass(PositionId.class)
public class Position {
    @Id
    @Column(nullable = false)
    private Long accountId;
    
    @Id
    @Column(nullable = false)
    private String symbol;
    
    @Column(nullable = false)
    private Integer quantity;

    // Part of the quantity held for accepted SELL orders; never more than the quantity
    @Column(nullable = false)
    private Integer reservedQuantity = 0;
    
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal averageCost;

    public Position() {
    }

    public Position(Long accountId, String symbol, Integer quantity, BigDecimal averageCost) {
        validateConstructorArgs(accountId, symbol, quantity, averageCost);
        this.accountId = accountId;
        this.symbol = InputNormalizer.normalize(symbol);
        this.quantity = quantity;
        this.averageCost = new BigDecimal(averageCost.toPlainString());
    }

    public Position(Position other) {
        if (other == null) {
            throw new IllegalArgumentException("Source position cannot be null");
        }
        this.accountId = other.accountId;
        this.symbol = other.symbol;
        this.quantity = other.quantity;
        this.reservedQuantity = other.reservedQuantity;
        this.averageCost = new BigDecimal(other.averageCost.toPlainString());
    }

    private void validateConstructorArgs(Long accountId, String symbol, Integer quantity, BigDecimal averageCost) {
        if (accountId == null || accountId <= 0) {
            throw new IllegalArgumentException("Valid account ID is required");
        }
        if (symbol == null || InputNormalizer.normalize(symbol).isEmpty()) {
            throw new IllegalArgumentException("Symbol cannot be null or empty");
        }
        if (quantity == null || quantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative");
        }
        if (averageCost == null) {
            throw new IllegalArgumentException("Average cost cannot be null");
        }
        if (averageCost.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Average cost cannot be negative");
        }
    }

    public void apply(Integer quantity, BigDecimal price) {
        if (quantity == null || quantity == 0) {
            throw new IllegalArgumentException("Quantity must be non-zero");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }

        // Validate quantity bounds: new quantity cannot be negative
        int newQuantity = this.quantity + quantity;
        if (newQuantity < 0) {
            throw new IllegalArgumentException(
                    String.format("Quantity cannot go negative: current=%d, change=%d, result=%d",
                            this.quantity, quantity, newQuantity));
        }
        // Shares reserved for accepted SELL orders cannot be sold by another one
        if (newQuantity < reservedQuantity) {
            throw new IllegalArgumentException(
                    String.format("Quantity cannot go below the reserved quantity: reserved=%d, result=%d",
                            reservedQuantity, newQuantity));
        }

        if (quantity < 0) {
            // Selling realises part of the holding at the existing cost basis; only
            // buys change the average cost
            this.quantity = newQuantity;
            if (newQuantity == 0) {
                this.averageCost = BigDecimal.ZERO;
            }
        } else if (this.quantity == 0) {
            this.averageCost = new BigDecimal(price.toPlainString());
            this.quantity = quantity;
        } else {
            BigDecimal totalCost = this.averageCost.multiply(BigDecimal.valueOf(this.quantity))
                    .add(price.multiply(BigDecimal.valueOf(quantity)));
            this.quantity += quantity;
            this.averageCost = totalCost.divide(BigDecimal.valueOf(this.quantity), 2, java.math.RoundingMode.HALF_UP);
        }
    }

    /**
     * Holds shares for an accepted SELL order, so no other order can sell them
     * until {@link #release} gives them back.
     *
     * @throws InsufficientHoldingsException if fewer shares are available than requested
     */
    public void reserve(int shares) {
        if (shares <= 0) {
            throw new IllegalArgumentException("Reserved quantity must be positive");
        }
        if (getAvailableQuantity() < shares) {
            throw new InsufficientHoldingsException("Insufficient holdings for symbol: " + symbol);
        }
        this.reservedQuantity += shares;
    }

    /**
     * Gives back shares held by {@link #reserve}, when the order is filled,
     * cancelled or rejected.
     *
     * @throws IllegalStateException if more is released than is reserved
     */
    public void release(int shares) {
        if (shares <= 0) {
            throw new IllegalArgumentException("Released quantity must be positive");
        }
        if (reservedQuantity < shares) {
            throw new IllegalStateException(String.format(
                    "Cannot release %d shares of %s, only %d are reserved", shares, symbol, reservedQuantity));
        }
        this.reservedQuantity -= shares;
    }

    public BigDecimal marketValue(BigDecimal currentPrice) {
        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Current price must be non-negative");
        }
        return currentPrice.multiply(BigDecimal.valueOf(quantity));
    }

    // Getters
    public Long getAccountId() {
        return accountId;
    }

    public String getSymbol() {
        return symbol;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public Integer getReservedQuantity() {
        return reservedQuantity;
    }

    /**
     * @return the quantity less the shares reserved for accepted SELL orders
     */
    public int getAvailableQuantity() {
        return quantity - reservedQuantity;
    }

    public BigDecimal getAverageCost() {
        return new BigDecimal(averageCost.toPlainString());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        Position other = (Position) obj;
        return accountId != null && accountId.equals(other.accountId) &&
                symbol != null && symbol.equals(other.symbol);
    }

    @Override
    public int hashCode() {
        int result = accountId != null ? accountId.hashCode() : 0;
        result = 31 * result + (symbol != null ? symbol.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Position{" +
                "accountId=" + accountId +
                ", symbol='" + symbol + '\'' +
                ", quantity=" + quantity +
                ", reservedQuantity=" + reservedQuantity +
                ", averageCost=" + averageCost +
                '}';
    }
}