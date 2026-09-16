package com.neueda.leap.model;

import java.math.BigDecimal;
import java.time.Instant;
import com.neueda.leap.enums.AccountStatus;
import com.neueda.leap.exceptions.InsufficientFundsException;
import com.neueda.leap.time.Clock;

/**
 * Account domain entity.
 * 
 * Manages account data with core validations and business logic.
 * Follows Domain-Driven Design principles.
 */
public class Account {
    private Long id;
    private String accountId;
    private String holderName;
    private BigDecimal cashBalance;
    private AccountStatus status;
    private Integer version;
    private Instant lastUpdated;

    public Account() {
    }

    public Account(String accountId, String holderName, BigDecimal cashBalance, Clock clock) {
        validateConstructorArgs(accountId, holderName, cashBalance);
        this.accountId = accountId;
        this.holderName = holderName;
        this.cashBalance = new BigDecimal(cashBalance.toPlainString());
        this.status = AccountStatus.ACTIVE;
        this.version = 0;
        this.lastUpdated = clock.now();
    }

    public Account(Account other) {
        if (other == null) {
            throw new IllegalArgumentException("Source account cannot be null");
        }
        this.id = other.id;
        this.accountId = other.accountId;
        this.holderName = other.holderName;
        this.cashBalance = new BigDecimal(other.cashBalance.toPlainString());
        this.status = other.status;
        this.version = other.version;
        this.lastUpdated = other.lastUpdated;
    }

    private void validateConstructorArgs(String accountId, String holderName, BigDecimal cashBalance) {
        if (accountId == null || accountId.trim().isEmpty()) {
            throw new IllegalArgumentException("Account ID cannot be null or empty");
        }
        if (holderName == null || holderName.trim().isEmpty()) {
            throw new IllegalArgumentException("Holder name cannot be null or empty");
        }
        if (cashBalance == null) {
            throw new IllegalArgumentException("Cash balance cannot be null");
        }
        if (cashBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Cash balance cannot be negative");
        }
    }

    public void debit(BigDecimal amount) throws InsufficientFundsException {
        if (amount == null) {
            throw new IllegalArgumentException("Debit amount cannot be null");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive");
        }
        if (cashBalance.compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient funds");
        }
        this.cashBalance = cashBalance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Credit amount cannot be null");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }
        this.cashBalance = cashBalance.add(amount);
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getHolderName() {
        return holderName;
    }

    public BigDecimal getCashBalance() {
        return new BigDecimal(cashBalance.toPlainString());
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Status cannot be null");
        }
        this.status = status;
    }

    public Integer getVersion() {
        return version;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        Account other = (Account) obj;
        return accountId != null && accountId.equals(other.accountId);
    }

    @Override
    public int hashCode() {
        return accountId != null ? accountId.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "Account{" +
                "id=" + id +
                ", accountId='" + accountId + '\'' +
                ", holderName='" + holderName + '\'' +
                ", cashBalance=" + cashBalance +
                ", status=" + status +
                ", version=" + version +
                ", lastUpdated=" + lastUpdated +
                '}';
    }
}