package com.neueda.leap.model;

import com.neueda.leap.utils.InputNormalizer;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Instrument domain entity.
 * 
 * Manages tradable instrument data with validations.
 * Follows Domain-Driven Design principles.
 */
@Entity
@Table(name = "instruments")
public class Instrument {
    // symbol is the primary key of the instruments table
    @Id
    @Column(nullable = false)
    private String symbol;
    
    @Column(nullable = false)
    private String name;
    
    @Column(nullable = false)
    private String assetClass;
    
    // The column is CHAR(3), so map it as CHAR rather than the default VARCHAR
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;
    
    @Column(nullable = false)
    private boolean tradable;

    // Latest market price from the price feed; null until the first price arrives
    @Column(precision = 18, scale = 2)
    private BigDecimal lastPrice;

    private Instant lastPriceOn;

    public Instrument() {
    }

    public Instrument(String symbol, String name, String assetClass, String currency, boolean tradable) {
        validateConstructorArgs(symbol, name, assetClass, currency);
        this.symbol = InputNormalizer.normalize(symbol);
        this.name = name;
        this.assetClass = InputNormalizer.normalize(assetClass);
        this.currency = InputNormalizer.normalize(currency);
        this.tradable = tradable;
    }

    public Instrument(Instrument other) {
        if (other == null) {
            throw new IllegalArgumentException("Source instrument cannot be null");
        }
        this.symbol = other.symbol;
        this.name = other.name;
        this.assetClass = other.assetClass;
        this.currency = other.currency;
        this.tradable = other.tradable;
        this.lastPrice = other.lastPrice;
        this.lastPriceOn = other.lastPriceOn;
    }

    private void validateConstructorArgs(String symbol, String name, String assetClass, String currency) {
        if (symbol == null || InputNormalizer.normalize(symbol).isEmpty()) {
            throw new IllegalArgumentException("Symbol cannot be null or empty");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Name cannot be null or empty");
        }
        if (assetClass == null || InputNormalizer.normalize(assetClass).isEmpty()) {
            throw new IllegalArgumentException("Asset class cannot be null or empty");
        }
        if (currency == null || InputNormalizer.normalize(currency).isEmpty()) {
            throw new IllegalArgumentException("Currency cannot be null or empty");
        }
    }

    public boolean isTradable() {
        return tradable;
    }

    /**
     * Records a new market price for the instrument.
     *
     * @param price the price, rounded to cents; must be positive
     * @param at    when the price was observed
     */
    public void updatePrice(BigDecimal price, Instant at) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }
        if (at == null) {
            throw new IllegalArgumentException("Price time cannot be null");
        }
        this.lastPrice = price.setScale(2, RoundingMode.HALF_UP);
        this.lastPriceOn = at;
    }

    /**
     * @return the latest market price, or null if no price has arrived yet
     */
    public BigDecimal getLastPrice() {
        return lastPrice;
    }

    public Instant getLastPriceOn() {
        return lastPriceOn;
    }

    // Getters
    public String getSymbol() {
        return symbol;
    }

    public String getName() {
        return name;
    }

    public String getAssetClass() {
        return assetClass;
    }

    public String getCurrency() {
        return currency;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        Instrument other = (Instrument) obj;
        return symbol != null && symbol.equals(other.symbol);
    }

    @Override
    public int hashCode() {
        return symbol != null ? symbol.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "Instrument{" +
                "symbol='" + symbol + '\'' +
                ", name='" + name + '\'' +
                ", assetClass='" + assetClass + '\'' +
                ", currency='" + currency + '\'' +
                ", tradable=" + tradable +
                '}';
    }

    public void setSymbol(String symbol) {
        if (symbol == null || InputNormalizer.normalize(symbol).isEmpty()) {
            throw new IllegalArgumentException("Symbol cannot be null or empty");
        }
        this.symbol = InputNormalizer.normalize(symbol);
    }

    public void setName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Name cannot be null or empty");
        }
        this.name = name;
    }

    public void setAssetClass(String assetClass) {
        if (assetClass == null || InputNormalizer.normalize(assetClass).isEmpty()) {
            throw new IllegalArgumentException("Asset class cannot be null or empty");
        }
        this.assetClass = InputNormalizer.normalize(assetClass);
    }

    public void setCurrency(String currency) {
        if (currency == null || InputNormalizer.normalize(currency).isEmpty()) {
            throw new IllegalArgumentException("Currency cannot be null or empty");
        }
        this.currency = InputNormalizer.normalize(currency);
    }

    public void setTradable(boolean tradable) {
        this.tradable = tradable;
    }
}