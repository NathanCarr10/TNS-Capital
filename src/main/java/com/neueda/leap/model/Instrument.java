package com.neueda.leap.model;

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
        this.symbol = symbol;
        this.name = name;
        this.assetClass = assetClass;
        this.currency = currency;
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
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("Symbol cannot be null or empty");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Name cannot be null or empty");
        }
        if (assetClass == null || assetClass.trim().isEmpty()) {
            throw new IllegalArgumentException("Asset class cannot be null or empty");
        }
        if (currency == null || currency.trim().isEmpty()) {
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
}