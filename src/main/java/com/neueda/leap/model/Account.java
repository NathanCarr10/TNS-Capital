package com.neueda.leap.model;

import java.math.BigDecimal;
import java.time.Instant;
import com.neueda.leap.enums.AccountStatus;
import com.neueda.leap.exceptions.InsufficientFundsException;
import com.neueda.leap.time.Clock;
import jakarta.persistence.*;

/**
 * Account domain entity.
 * 
 * Manages account data with core validations and business logic.
 * Follows Domain-Driven Design principles.
 */
@Entity
@Table(name = "accounts")
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_number", unique = true, nullable = false)
    private String accountNumber;

    @Column(nullable = false)
    private String holderName;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal cashBalance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;

    @Version
    private Integer version;

    @Column(nullable = false)
    private Instant lastUpdated;

    // JWT subject (username) of the customer who owns this account
    @Column(name = "owner_username")
    private String ownerUsername;

    public Account() {
    }

    public Account(String accountNumber, String holderName, BigDecimal cashBalance, Clock clock) {
        validateConstructorArgs(accountNumber, holderName, cashBalance);
        this.accountNumber = accountNumber;
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
        // this.id = other.id;
        this.accountNumber = other.accountNumber;
        this.holderName = other.holderName;
        this.cashBalance = new BigDecimal(other.cashBalance.toPlainString());
        this.status = other.status;
        this.version = other.version;
        this.lastUpdated = other.lastUpdated;
        this.ownerUsername = other.ownerUsername;
    }

    private void validateConstructorArgs(String accountNumber, String holderName, BigDecimal cashBalance) {
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Account number cannot be null or empty");
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

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getHolderName() {
        return holderName;
    }

    public void setHolderName(String holderName) {
        if (holderName == null || holderName.trim().isEmpty()) {
            throw new IllegalArgumentException("Holder name cannot be null or empty");
        }
        this.holderName = holderName;
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

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public void setOwnerUsername(String ownerUsername) {
        this.ownerUsername = ownerUsername;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        Account other = (Account) obj;
        return accountNumber != null && accountNumber.equals(other.accountNumber);
    }

    @Override
    public int hashCode() {
        return accountNumber != null ? accountNumber.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "Account{" +
                "id=" + id +
                ", accountNumber='" + accountNumber + '\'' +
                ", holderName='" + holderName + '\'' +
                ", cashBalance=" + cashBalance +
                ", status=" + status +
                ", version=" + version +
                ", lastUpdated=" + lastUpdated +
                ", ownerUsername='" + ownerUsername + '\'' +
                '}';
    }
}