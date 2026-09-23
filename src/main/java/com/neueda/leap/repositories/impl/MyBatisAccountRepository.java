package com.neueda.leap.repositories.impl;

import com.neueda.leap.model.Account;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.mappers.AccountMapper;
import java.util.Optional;
import java.util.Objects;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Primary;

/**
 * MyBatis implementation of AccountRepository.
 * 
 * Persists Account entities to the database via MyBatis mapper.
 */
@Repository
@Primary
public class MyBatisAccountRepository implements AccountRepository {
    private final AccountMapper accountMapper;

    public MyBatisAccountRepository(AccountMapper accountMapper) {
        this.accountMapper = Objects.requireNonNull(accountMapper);
    }

    @Override
    public Optional<Account> findById(Long accountId) {
        Objects.requireNonNull(accountId, "Account ID cannot be null");
        Account account = accountMapper.findById(accountId);
        return Optional.ofNullable(account);
    }

    @Override
    public void save(Account account) {
        Objects.requireNonNull(account, "Account cannot be null");
        accountMapper.save(account);
    }
}
