package com.neueda.leap.repositories.impl;

import com.neueda.leap.model.Account;
import com.neueda.leap.repositories.AccountRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery;

/**
 * In-memory implementation of AccountRepository for testing.
 * 
 * Wraps a Map<Long, Account> for storage.
 * Provides minimal JpaRepository implementation for test support.
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
    public Optional<Account> findByAccountId(String accountId) {
        return storage.values().stream()
                .filter(account -> account.getAccountId().equals(accountId))
                .findFirst();
    }

    @Override
    public <S extends Account> S save(S account) {
        Objects.requireNonNull(account, "Account cannot be null");
        storage.put(account.getId(), account);
        return account;
    }

    @Override
    public void deleteById(Long id) {
        storage.remove(id);
    }

    @Override
    public void delete(Account entity) {
        if (entity != null && entity.getId() != null) {
            storage.remove(entity.getId());
        }
    }

    @Override
    public long count() {
        return storage.size();
    }

    @Override
    public boolean existsById(Long id) {
        return storage.containsKey(id);
    }

    // Stub implementations for JpaRepository methods
    @Override
    public List<Account> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public List<Account> findAllById(Iterable<Long> ids) {
        List<Account> result = new ArrayList<>();
        for (Long id : ids) {
            findById(id).ifPresent(result::add);
        }
        return result;
    }

    @Override
    public <S extends Account> List<S> saveAll(Iterable<S> entities) {
        List<S> result = new ArrayList<>();
        for (S entity : entities) {
            result.add(save(entity));
        }
        return result;
    }

    @Override
    public void flush() {
        // No-op for in-memory implementation
    }

    @Override
    public <S extends Account> S saveAndFlush(S entity) {
        return save(entity);
    }

    @Override
    public <S extends Account> List<S> saveAllAndFlush(Iterable<S> entities) {
        return saveAll(entities);
    }

    @Override
    public void deleteAllInBatch(Iterable<Account> entities) {
        for (Account entity : entities) {
            delete(entity);
        }
    }

    @Override
    public void deleteAllByIdInBatch(Iterable<Long> ids) {
        for (Long id : ids) {
            deleteById(id);
        }
    }

    @Override
    public void deleteAllInBatch() {
        storage.clear();
    }

    @Override
    public Account getById(Long id) {
        return findById(id).orElse(null);
    }

    @Override
    public Account getReferenceById(Long id) {
        return findById(id).orElse(null);
    }

    @Override
    @Deprecated(since = "3.0")
    public Account getOne(Long id) {
        return findById(id).orElse(null);
    }

    @Override
    public <S extends Account> List<S> findAll(Example<S> example) {
        return new ArrayList<>();
    }

    @Override
    public <S extends Account> List<S> findAll(Example<S> example, Sort sort) {
        return new ArrayList<>();
    }

    @Override
    public <S extends Account> Optional<S> findOne(Example<S> example) {
        return Optional.empty();
    }

    @Override
    public <S extends Account> long count(Example<S> example) {
        return 0;
    }

    @Override
    public <S extends Account> boolean exists(Example<S> example) {
        return false;
    }

    @Override
    public <S extends Account, R> R findBy(Example<S> example, java.util.function.Function<FetchableFluentQuery<S>, R> queryFunction) {
        return null;
    }

    @Override
    public void deleteAll(Iterable<? extends Account> entities) {
        for (Account entity : entities) {
            delete(entity);
        }
    }

    @Override
    public void deleteAll() {
        storage.clear();
    }

    @Override
    public void deleteAllById(Iterable<? extends Long> ids) {
        for (Long id : ids) {
            deleteById(id);
        }
    }

    @Override
    public List<Account> findAll(Sort sort) {
        return new ArrayList<>(storage.values());
    }

    @Override
    public Page<Account> findAll(Pageable pageable) {
        return null;
    }

    @Override
    public <S extends Account> Page<S> findAll(Example<S> example, Pageable pageable) {
        return null;
    }
}
