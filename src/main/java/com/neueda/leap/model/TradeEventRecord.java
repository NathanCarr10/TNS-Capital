package com.neueda.leap.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One order lifecycle event from the trade-events topic, kept for the audit
 * trail (table trade_event_log).
 */
@Entity
@Table(name = "trade_event_log")
public class TradeEventRecord {
    @Id
    private String eventId;

    @Column(nullable = false)
    private UUID orderId;

    @Column(nullable = false)
    private Long accountId;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String side;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal price;

    private String previousStatus;

    @Column(nullable = false)
    private String newStatus;

    private String reason;

    @Column(nullable = false)
    private Instant receivedOn;

    protected TradeEventRecord() {
        // JPA no-arg constructor
    }

    public TradeEventRecord(String eventId, UUID orderId, Long accountId, String symbol, String side,
            Integer quantity, BigDecimal price, String previousStatus, String newStatus, String reason,
            Instant receivedOn) {
        this.eventId = Objects.requireNonNull(eventId);
        this.orderId = Objects.requireNonNull(orderId);
        this.accountId = Objects.requireNonNull(accountId);
        this.symbol = Objects.requireNonNull(symbol);
        this.side = Objects.requireNonNull(side);
        this.quantity = Objects.requireNonNull(quantity);
        this.price = Objects.requireNonNull(price);
        this.previousStatus = previousStatus;
        this.newStatus = Objects.requireNonNull(newStatus);
        this.reason = reason;
        this.receivedOn = Objects.requireNonNull(receivedOn);
    }

    public String getEventId() {
        return eventId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getSide() {
        return side;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getPreviousStatus() {
        return previousStatus;
    }

    public String getNewStatus() {
        return newStatus;
    }

    public String getReason() {
        return reason;
    }

    public Instant getReceivedOn() {
        return receivedOn;
    }
}
