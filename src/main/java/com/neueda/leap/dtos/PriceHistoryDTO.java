package com.neueda.leap.dtos;

import com.neueda.leap.model.PriceHistory;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Data Transfer Object for PriceHistory.
 * 
 * Used for API responses to decouple internal domain model from external contracts.
 */
public class PriceHistoryDTO {
    private String symbol;
    private LocalDate date;
    private BigDecimal openPrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal closePrice;
    private BigDecimal adjClosePrice;
    private Long volume;
    
    public PriceHistoryDTO() {
    }
    
    public PriceHistoryDTO(String symbol, LocalDate date, BigDecimal openPrice,
            BigDecimal highPrice, BigDecimal lowPrice, BigDecimal closePrice,
            BigDecimal adjClosePrice, Long volume) {
        this.symbol = symbol;
        this.date = date;
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.closePrice = closePrice;
        this.adjClosePrice = adjClosePrice;
        this.volume = volume;
    }
    
    public static PriceHistoryDTO fromEntity(PriceHistory entity) {
        return new PriceHistoryDTO(
                entity.getSymbol(),
                entity.getDate(),
                entity.getOpenPrice(),
                entity.getHighPrice(),
                entity.getLowPrice(),
                entity.getClosePrice(),
                entity.getAdjClosePrice(),
                entity.getVolume()
        );
    }
    
    // Getters and Setters
    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public BigDecimal getOpenPrice() {
        return openPrice;
    }

    public void setOpenPrice(BigDecimal openPrice) {
        this.openPrice = openPrice;
    }

    public BigDecimal getHighPrice() {
        return highPrice;
    }

    public void setHighPrice(BigDecimal highPrice) {
        this.highPrice = highPrice;
    }

    public BigDecimal getLowPrice() {
        return lowPrice;
    }

    public void setLowPrice(BigDecimal lowPrice) {
        this.lowPrice = lowPrice;
    }

    public BigDecimal getClosePrice() {
        return closePrice;
    }

    public void setClosePrice(BigDecimal closePrice) {
        this.closePrice = closePrice;
    }

    public BigDecimal getAdjClosePrice() {
        return adjClosePrice;
    }

    public void setAdjClosePrice(BigDecimal adjClosePrice) {
        this.adjClosePrice = adjClosePrice;
    }

    public Long getVolume() {
        return volume;
    }

    public void setVolume(Long volume) {
        this.volume = volume;
    }
}
