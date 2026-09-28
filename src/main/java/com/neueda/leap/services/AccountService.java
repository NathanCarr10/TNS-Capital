package com.neueda.leap.services;

import com.neueda.leap.model.Account;
import com.neueda.leap.enums.AccountStatus;
import com.neueda.leap.exceptions.AccountNotFoundException;
import com.neueda.leap.exceptions.AccountNotActiveException;
import com.neueda.leap.repositories.AccountRepository;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provides account business logic: retrieval, validation, and account status management.
 * 
 * Decouples account operations from the repository layer, ensuring consistency and
 * maintaining layered architecture principles.
 */
@Service
@Transactional
public class AccountService {
    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = Objects.requireNonNull(accountRepository);
    }

    /**
     * Retrieves an account by ID.
     *
     * @param accountId the account ID to retrieve
     * @return the Account if found
     * @throws AccountNotFoundException if account is not found
     * @throws IllegalArgumentException if accountId is null
     */
    public Account getAccountById(Long accountId) {
        Objects.requireNonNull(accountId, "Account ID cannot be null");
        
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
    }

    /**
     * Retrieves an account by ID as an Optional.
     *
     * @param accountId the account ID to retrieve
     * @return Optional containing the account if found, empty otherwise
     * @throws IllegalArgumentException if accountId is null
     */
    public Optional<Account> findAccountById(Long accountId) {
        Objects.requireNonNull(accountId, "Account ID cannot be null");
        
        return accountRepository.findById(accountId);
    }

    /**
     * Gets the cash balance for an account.
     *
     * @param accountId the account ID
     * @return the cash balance
     * @throws AccountNotFoundException if account is not found
     */
    public BigDecimal getCashBalance(Long accountId) {
        Account account = getAccountById(accountId);
        return account.getCashBalance();
    }

    /**
     * Checks if an account is active.
     *
     * @param accountId the account ID
     * @return true if account is active, false otherwise
     * @throws AccountNotFoundException if account is not found
     */
    public boolean isAccountActive(Long accountId) {
        Account account = getAccountById(accountId);
        return account.isActive();
    }

    /**
     * Validates that an account is active.
     *
     * @param accountId the account ID
     * @throws AccountNotFoundException if account is not found
     * @throws AccountNotActiveException if account is not active
     */
    public void validateAccountActive(Long accountId) {
        Account account = getAccountById(accountId);
        if (!account.isActive()) {
            throw new AccountNotActiveException("Account is not active: " + accountId);
        }
    }

    /**
     * Validates that an account has sufficient funds for a transaction.
     *
     * @param accountId the account ID
     * @param amount the amount required
     * @throws AccountNotFoundException if account is not found
     * @throws IllegalArgumentException if amount is null or non-positive
     */
    public void validateSufficientFunds(Long accountId, BigDecimal amount) {
        Objects.requireNonNull(amount, "Amount cannot be null");
        
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        
        BigDecimal balance = getCashBalance(accountId);
        if (balance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient funds available");
        }
    }

    /**
     * Persists or updates an account.
     *
     * @param account the account to save
     * @throws IllegalArgumentException if account is null
     */
    public void saveAccount(Account account) {
        Objects.requireNonNull(account, "Account cannot be null");
        accountRepository.save(account);
    }
}
