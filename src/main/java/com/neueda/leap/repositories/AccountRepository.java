package com.neueda.leap.repositories;

import com.neueda.leap.model.Account;

import java.util.List;
import java.util.Optional;

/**
 * Repository abstraction for Account persistence.
 * 
 * Decouples business logic from storage implementation.
 * Enables testing with mock repositories and future Spring Data JPA
 * integration.
 */
public interface AccountRepository {
    /**
     * Finds an account by ID.
     *
     * @param accountId the account ID to search for
     * @return Optional containing the account if found, empty otherwise
     */
    Optional<Account> findById(Long accountId);

    /**
     * Saves or updates an account.
     *
     * @param account the account to persist
     * @throws IllegalArgumentException if account is null
     */
    void save(Account account);
    
     /**
     * Finds all accounts.
     *
     * @return a collection of all accounts
     */
    List<Account> findAll();
}
