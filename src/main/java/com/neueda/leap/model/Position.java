package com.neueda.leap.model;

import java.math.BigDecimal;

/**
 * Position domain entity.
 * 
 * Manages position data with core validations and business logic.
 * Follows Domain-Driven Design principles.
 */
public class Position {
    private Long accountId;
    private String symbol;
    private Integer quantity;
    private BigDecimal averageCost;

    public Position() {
    }

    public Position(Long accountId, String symbol, Integer quantity, BigDecimal averageCost) {
        validateConstructorArgs(accountId, symbol, quantity, averageCost);
        this.accountId = accountId;
        this.symbol = symbol;
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
        this.averageCost = new BigDecimal(other.averageCost.toPlainString());
    }

    private void validateConstructorArgs(Long accountId, String symbol, Integer quantity, BigDecimal averageCost) {
        if (accountId == null || accountId <= 0) {
            throw new IllegalArgumentException("Valid account ID is required");
        }
        if (symbol == null || symbol.trim().isEmpty()) {
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
        if (this.quantity == 0) {
            this.averageCost = new BigDecimal(price.toPlainString());
            this.quantity = quantity;
        } else {
            BigDecimal totalCost = this.averageCost.multiply(BigDecimal.valueOf(this.quantity))
                    .add(price.multiply(BigDecimal.valueOf(quantity)));
            this.quantity += quantity;
            this.averageCost = totalCost.divide(BigDecimal.valueOf(this.quantity), 2, java.math.RoundingMode.HALF_UP);
        }
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
                ", averageCost=" + averageCost +
                '}';
    }
}