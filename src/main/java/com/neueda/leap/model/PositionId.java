package com.neueda.leap.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key for Position entity.
 * 
 * Required for @IdClass mapping with accountId and symbol.
 */
public class PositionId implements Serializable {
    private Long accountId;
    private String symbol;

    public PositionId() {
    }

    public PositionId(Long accountId, String symbol) {
        this.accountId = accountId;
        this.symbol = symbol;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        PositionId other = (PositionId) obj;
        return Objects.equals(accountId, other.accountId) && Objects.equals(symbol, other.symbol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, symbol);
    }

    @Override
    public String toString() {
        return "PositionId{" +
                "accountId=" + accountId +
                ", symbol='" + symbol + '\'' +
                '}';
    }
}
