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

    @Column(nullable = false, length = 320)
    private String email;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal cashBalance;

    // Part of the cash balance held for accepted BUY orders; never more than the balance
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal reservedCash = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;

    @Version
    private Integer version;

    @Column(nullable = false, updatable = false)
    private Instant openedOn;

    @Column(nullable = false)
    private Instant lastUpdated;

    public Account() {
    }

    public Account(String accountNumber, String holderName, BigDecimal cashBalance, Clock clock) {
        this(accountNumber, holderName, accountNumber.toLowerCase() + "@example.invalid", cashBalance, clock);
    }

    public Account(String accountNumber, String holderName, String email, BigDecimal cashBalance, Clock clock) {
        validateConstructorArgs(accountNumber, holderName, cashBalance);
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email cannot be null or empty");
        }
        this.accountNumber = accountNumber;
        this.holderName = holderName;
        this.email = email.trim().toLowerCase();
        this.cashBalance = new BigDecimal(cashBalance.toPlainString());
        this.status = AccountStatus.ACTIVE;
        this.version = 0;
        this.openedOn = clock.now();
        this.lastUpdated = this.openedOn;
    }

    public Account(Account other) {
        if (other == null) {
            throw new IllegalArgumentException("Source account cannot be null");
        }
        // The copy is a new, unsaved account, so it gets its own id when saved
        this.id = null;
        this.accountNumber = other.accountNumber;
        this.holderName = other.holderName;
        this.email = other.email;
        this.cashBalance = new BigDecimal(other.cashBalance.toPlainString());
        this.reservedCash = new BigDecimal(other.reservedCash.toPlainString());
        this.status = other.status;
        this.version = other.version;
        this.openedOn = other.openedOn;
        this.lastUpdated = other.lastUpdated;
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

    /**
     * Takes cash out of the account. Only the available cash can be debited:
     * cash reserved for accepted orders stays put until they release it.
     */
    public void debit(BigDecimal amount) throws InsufficientFundsException {
        requirePositive(amount, "Debit");
        if (getAvailableCash().compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient funds");
        }
        this.cashBalance = cashBalance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        requirePositive(amount, "Credit");
        this.cashBalance = cashBalance.add(amount);
    }

    /**
     * Holds cash for an accepted BUY order. The cash stays in the balance but
     * can no longer be debited or reserved again until {@link #release} gives
     * it back.
     *
     * @throws InsufficientFundsException if the available cash is less than the amount
     */
    public void reserve(BigDecimal amount) throws InsufficientFundsException {
        requirePositive(amount, "Reserve");
        if (getAvailableCash().compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient funds");
        }
        this.reservedCash = reservedCash.add(amount);
    }

    /**
     * Gives back cash held by {@link #reserve}, when the order is filled,
     * cancelled or rejected.
     *
     * @throws IllegalStateException if more is released than is reserved
     */
    public void release(BigDecimal amount) {
        requirePositive(amount, "Release");
        if (reservedCash.compareTo(amount) < 0) {
            throw new IllegalStateException(String.format(
                    "Cannot release %s, only %s is reserved", amount.toPlainString(), reservedCash.toPlainString()));
        }
        this.reservedCash = reservedCash.subtract(amount);
    }

    private static void requirePositive(BigDecimal amount, String what) {
        if (amount == null) {
            throw new IllegalArgumentException(what + " amount cannot be null");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(what + " amount must be positive");
        }
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    // Every change (cash, status, holder name) is saved through JPA, so stamp
    // the time here rather than in each method
    @PreUpdate
    void touch() {
        this.lastUpdated = Instant.now();
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

    public String getEmail() {
        return email;
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

    public BigDecimal getReservedCash() {
        return new BigDecimal(reservedCash.toPlainString());
    }

    /**
     * @return the cash balance less the cash reserved for accepted orders
     */
    public BigDecimal getAvailableCash() {
        return cashBalance.subtract(reservedCash);
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

    public Instant getOpenedOn() {
        return openedOn;
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
                ", reservedCash=" + reservedCash +
                ", status=" + status +
                ", version=" + version +
                ", openedOn=" + openedOn +
                ", lastUpdated=" + lastUpdated +
                '}';
    }
}