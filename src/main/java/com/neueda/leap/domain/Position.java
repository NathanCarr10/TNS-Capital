package com.neueda.leap.domain;

import java.math.BigDecimal;

public class Position {
    private Long accountId;
    private String symbol;
    private Integer quantity;
    private BigDecimal averageCost;

    public Position() {}

    public Position(Long accountId, String symbol, Integer quantity, BigDecimal averageCost) {
        this.accountId = accountId;
        this.symbol = symbol;
        this.quantity = quantity;
        this.averageCost = averageCost;
    }


    public void apply(Integer quantity, BigDecimal price) {
        if (this.quantity == 0) {
            this.averageCost = price;
            this.quantity = quantity;
        } else {
            BigDecimal totalCost = this.averageCost.multiply(BigDecimal.valueOf(this.quantity))
                    .add(price.multiply(BigDecimal.valueOf(quantity)));
            this.quantity += quantity;
            this.averageCost = totalCost.divide(BigDecimal.valueOf(this.quantity), 2, java.math.RoundingMode.HALF_UP);
        }
    }


    public BigDecimal marketValue(BigDecimal currentPrice) {
        return currentPrice.multiply(BigDecimal.valueOf(quantity));
    }

    // Getters
    public Long getAccountId() { return accountId; }
    public String getSymbol() { return symbol; }
    public Integer getQuantity() { return quantity; }
    public BigDecimal getAverageCost() { return averageCost; }
}