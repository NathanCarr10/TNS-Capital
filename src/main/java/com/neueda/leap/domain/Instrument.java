package com.neueda.leap.domain;

public class Instrument {
    private Long id;
    private String symbol;
    private String name;
    private String assetClass;
    private String currency;
    private boolean tradable;

    public Instrument() {}

    public Instrument(String symbol, String name, String assetClass, String currency, boolean tradable) {
        this.symbol = symbol;
        this.name = name;
        this.assetClass = assetClass;
        this.currency = currency;
        this.tradable = tradable;
    }

    public boolean isTradable() {
        return tradable;
    }

    // Getters
    public Long getId() { return id; }
    public String getSymbol() { return symbol; }
    public String getName() { return name; }
    public String getAssetClass() { return assetClass; }
    public String getCurrency() { return currency; }
}