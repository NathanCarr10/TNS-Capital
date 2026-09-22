package com.neueda.leap.repositories.impl;

import com.neueda.leap.model.Account;
import com.neueda.leap.repositories.AccountRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * In-memory implementation of AccountRepository.
 * 
 * Wraps a Map<Long, Account> for storage.
 * Suitable for testing and non-persistent use; replace with Spring Data JPA
 * implementation for production.
 */
public class InMemoryAccountRepository implements AccountRepository {
    private final Map<Long, Account> storage;

    public InMemoryAccountRepository(Map<Long, Account> storage) {
        this.storage = Objects.requireNonNull(storage, "Storage map cannot be null");
    }

    @Override
    public Optional<Account> findById(Long accountId) {
        return Optional.ofNullable(storage.get(accountId));
    }

    @Override
    public void save(Account account) {
        Objects.requireNonNull(account, "Account cannot be null");
        storage.put(account.getId(), account);
    }

    @Override
    public List<Account> findAll() {
        return new ArrayList<>(storage.values());
    }
}
