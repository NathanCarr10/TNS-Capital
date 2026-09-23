package com.neueda.leap.model;

import com.neueda.leap.utils.InputNormalizer;

/**
 * Instrument domain entity.
 * 
 * Manages tradable instrument data with validations.
 * Follows Domain-Driven Design principles.
 */
public class Instrument {
    private Long id;
    private String symbol;
    private String name;
    private String assetClass;
    private String currency;
    private boolean tradable;

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
        this.id = other.id;
        this.symbol = other.symbol;
        this.name = other.name;
        this.assetClass = other.assetClass;
        this.currency = other.currency;
        this.tradable = other.tradable;
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

    // Getters
    public Long getId() {
        return id;
    }

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
                "id=" + id +
                ", symbol='" + symbol + '\'' +
                ", name='" + name + '\'' +
                ", assetClass='" + assetClass + '\'' +
                ", currency='" + currency + '\'' +
                ", tradable=" + tradable +
                '}';
    }

    public void setId(Long id) {
        this.id = id;
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