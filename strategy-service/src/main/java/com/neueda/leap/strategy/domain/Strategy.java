package com.neueda.leap.strategy.domain;

import com.neueda.leap.strategy.rules.RuleFactory;
import com.neueda.leap.strategy.rules.RuleType;
import com.neueda.leap.strategy.rules.TradingRule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * A trading rule a trader owns, on one instrument (table strategies).
 */
@Entity
@Table(name = "strategies")
public class Strategy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private Long accountId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, updatable = false)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false)
    private RuleType ruleType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rule_params", nullable = false, columnDefinition = "jsonb")
    private Map<String, Number> ruleParams;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StrategyStatus status = StrategyStatus.PAUSED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Position position = Position.FLAT;

    @Column(name = "created_on", nullable = false, updatable = false)
    private Instant createdOn;

    @Column(name = "updated_on", nullable = false)
    private Instant updatedOn;

    @Version
    private int version;

    protected Strategy() {
        // For JPA
    }

    public Strategy(Long accountId, String name, String symbol, RuleType ruleType, Map<String, Number> ruleParams,
            int quantity) {
        this.accountId = accountId;
        this.name = name;
        this.symbol = symbol;
        this.ruleType = ruleType;
        this.ruleParams = ruleParams;
        this.quantity = quantity;
    }

    @PrePersist
    void created() {
        createdOn = Instant.now();
        updatedOn = createdOn;
    }

    @PreUpdate
    void updated() {
        updatedOn = Instant.now();
    }

    /**
     * @return the rule this strategy runs
     */
    public TradingRule rule() {
        return RuleFactory.create(ruleType, ruleParams);
    }

    public Long getId() {
        return id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSymbol() {
        return symbol;
    }

    public RuleType getRuleType() {
        return ruleType;
    }

    public Map<String, Number> getRuleParams() {
        return ruleParams;
    }

    public int getQuantity() {
        return quantity;
    }

    public StrategyStatus getStatus() {
        return status;
    }

    public void setStatus(StrategyStatus status) {
        this.status = status;
    }

    public Position getPosition() {
        return position;
    }

    public void setPosition(Position position) {
        this.position = position;
    }

    public Instant getCreatedOn() {
        return createdOn;
    }

    public Instant getUpdatedOn() {
        return updatedOn;
    }
}
