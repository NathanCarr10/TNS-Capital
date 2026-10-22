package com.neueda.leap.services;

import com.neueda.leap.enums.AccountStatus;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.model.Account;
import com.neueda.leap.model.Order;
import com.neueda.leap.model.Position;
import com.neueda.leap.exceptions.AccountAlreadyExistsException;
import com.neueda.leap.exceptions.AccountDeletionConflictException;
import com.neueda.leap.exceptions.AccountNotFoundException;
import com.neueda.leap.utils.InputNormalizer;
import com.neueda.leap.exceptions.PositionNotFoundException;
import com.neueda.leap.exceptions.AccountNotActiveException;
import com.neueda.leap.exceptions.InsufficientFundsException;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.repositories.PositionRepository;
import com.neueda.leap.time.Clock;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provides account business logic: retrieval, validation, and account status
 * management.
 * 
 * Decouples account operations from the repository layer, ensuring consistency
 * and
 * maintaining layered architecture principles.
 */
@Service
@Transactional
public class AccountService {
    private final AccountRepository accountRepository;
    private final PositionRepository positionRepository;
    private final OrderRepository orderRepository;
    private final Clock clock;

    public AccountService(AccountRepository accountRepository, PositionRepository positionRepository,
            OrderRepository orderRepository, Clock clock) {
        this.accountRepository = Objects.requireNonNull(accountRepository);
        this.positionRepository = Objects.requireNonNull(positionRepository);
        this.orderRepository = Objects.requireNonNull(orderRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional(readOnly = true)
    public List<Account> getAllAccounts() {
        return accountRepository.findAll();
    }

    /**
     * Opens a new ACTIVE account.
     *
     * @throws AccountAlreadyExistsException if the account number is taken
     */
    public Account createAccount(String accountNumber, String holderName, BigDecimal openingBalance) {
        if (accountRepository.findByAccountNumber(accountNumber).isPresent()) {
            throw new AccountAlreadyExistsException("Account number already exists: " + accountNumber);
        }
        return accountRepository.save(new Account(accountNumber, holderName, openingBalance, clock));
    }

    public Account updateHolderName(Long accountId, String holderName) {
        Account account = getAccountById(accountId);
        account.setHolderName(holderName);
        return accountRepository.save(account);
    }

    /**
     * Suspends an active account or reactivates a suspended one. Suspended
     * accounts cannot trade or move cash until they are active again.
     *
     * @throws IllegalArgumentException  if the status is not ACTIVE or SUSPENDED
     *                                   (closing goes through closeAccount)
     * @throws AccountNotActiveException if the account is closed
     */
    public Account updateStatus(Long accountId, AccountStatus status) {
        if (status != AccountStatus.ACTIVE && status != AccountStatus.SUSPENDED) {
            throw new IllegalArgumentException("Status must be ACTIVE or SUSPENDED: " + status);
        }
        Account account = getAccountById(accountId);
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new AccountNotActiveException("Account is closed: " + accountId);
        }
        account.setStatus(status);
        return accountRepository.save(account);
    }

    /**
     * Closes an account. The row is kept with status CLOSED rather than deleted,
     * so its orders, positions and history stay valid for the audit trail;
     * closed accounts cannot trade (business rule 2).
     *
     * A closed account can no longer withdraw or sell either, so it must be
     * empty first: anything left in it would be stranded.
     *
     * @throws AccountDeletionConflictException if the account has working (NEW)
     *                                          orders, holdings or cash
     */
    public Account closeAccount(Long accountId) {
        Account account = getAccountById(accountId);
        long workingOrders = orderRepository.findByAccountId(accountId).stream()
                .filter(order -> order.getStatus() == OrderStatus.NEW)
                .count();
        if (workingOrders > 0) {
            throw new AccountDeletionConflictException(
                    "Cannot close account with " + workingOrders + " active order(s)");
        }
        int holdings = positionRepository.findByAccountId(accountId).size();
        if (holdings > 0) {
            throw new AccountDeletionConflictException(
                    "Cannot close account with " + holdings + " holding(s); sell them first");
        }
        if (account.getCashBalance().signum() > 0) {
            throw new AccountDeletionConflictException(
                    "Cannot close account with a cash balance; withdraw it first");
        }
        account.setStatus(AccountStatus.CLOSED);
        return accountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public List<Position> getPositions(Long accountId) {
        getAccountById(accountId);
        return positionRepository.findByAccountId(accountId);
    }

    /**
     * One holding of an account.
     *
     * @throws AccountNotFoundException  if the account does not exist
     * @throws PositionNotFoundException if the account holds none of the symbol
     */
    @Transactional(readOnly = true)
    public Position getPosition(Long accountId, String symbol) {
        getAccountById(accountId);
        return positionRepository.findByAccountIdAndSymbol(accountId, InputNormalizer.normalize(symbol))
                .orElseThrow(() -> new PositionNotFoundException("Position not found: " + accountId + " " + symbol));
    }

    @Transactional(readOnly = true)
    public List<Order> getOrders(Long accountId) {
        getAccountById(accountId);
        return orderRepository.findByAccountId(accountId);
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
     * Credits cash to an active account.
     *
     * @throws AccountNotFoundException  if account is not found
     * @throws AccountNotActiveException if account is not active
     */
    public Account deposit(Long accountId, BigDecimal amount) {
        Account account = getAccountById(accountId);
        requireActive(account);
        account.credit(amount);
        return accountRepository.save(account);
    }

    /**
     * Debits cash from an active account; the balance can never go negative.
     *
     * @throws AccountNotFoundException   if account is not found
     * @throws AccountNotActiveException  if account is not active
     * @throws InsufficientFundsException if the balance is less than the amount
     */
    public Account withdraw(Long accountId, BigDecimal amount) {
        Account account = getAccountById(accountId);
        requireActive(account);
        account.debit(amount);
        return accountRepository.save(account);
    }

    private void requireActive(Account account) {
        if (!account.isActive()) {
            throw new AccountNotActiveException("Account is not active: " + account.getId());
        }
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
     * @throws AccountNotFoundException  if account is not found
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
     * @param amount    the amount required
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
