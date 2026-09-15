package com.neueda.leap.domain;

import java.math.BigDecimal;
import java.time.Instant;
import com.neueda.leap.enums.AccountStatus;
import com.neueda.leap.exceptions.InsufficientFundsException;

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

    public Account(String accountId, String holderName, BigDecimal cashBalance) {
        this.accountId = accountId;
        this.holderName = holderName;
        this.cashBalance = cashBalance;
        this.status = AccountStatus.ACTIVE;
        this.version = 0;
        this.lastUpdated = Instant.now();
    }

    public void debit(BigDecimal amount) throws InsufficientFundsException {
        if (cashBalance.compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient funds");
        }
        this.cashBalance = cashBalance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
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
        return cashBalance;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Integer getVersion() {
        return version;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }
}