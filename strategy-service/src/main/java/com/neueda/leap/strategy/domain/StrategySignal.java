package com.neueda.leap.strategy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A time a strategy's rule fired, and the order it placed (table strategy_signals).
 */
@Entity
@Table(name = "strategy_signals")
public class StrategySignal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "strategy_id", nullable = false, updatable = false)
    private Long strategyId;

    @Column(nullable = false, updatable = false)
    private String side;

    @Column(nullable = false, updatable = false)
    private BigDecimal price;

    @Column(name = "bar_time", nullable = false, updatable = false)
    private Instant barTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private SignalSource source;

    @Column(name = "replay_id", updatable = false)
    private String replayId;

    @Column(name = "order_id")
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SignalOutcome outcome;

    @Column
    private String detail;

    @Column(name = "created_on", nullable = false, updatable = false)
    private Instant createdOn;

    protected StrategySignal() {
        // For JPA
    }

    public StrategySignal(Long strategyId, String side, BigDecimal price, Instant barTime, SignalSource source,
            String replayId) {
        this.strategyId = strategyId;
        this.side = side;
        this.price = price;
        this.barTime = barTime;
        this.source = source;
        this.replayId = replayId;
    }

    @PrePersist
    void created() {
        createdOn = Instant.now();
    }

    public void placed(UUID orderId) {
        this.orderId = orderId;
        this.outcome = SignalOutcome.PLACED;
        this.detail = null;
    }

    public void failed(String reason) {
        this.outcome = SignalOutcome.FAILED;
        // The column holds 255 characters
        this.detail = reason == null || reason.length() <= 255 ? reason : reason.substring(0, 255);
    }

    public Long getId() {
        return id;
    }

    public Long getStrategyId() {
        return strategyId;
    }

    public String getSide() {
        return side;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Instant getBarTime() {
        return barTime;
    }

    public SignalSource getSource() {
        return source;
    }

    public String getReplayId() {
        return replayId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public SignalOutcome getOutcome() {
        return outcome;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getCreatedOn() {
        return createdOn;
    }
}
