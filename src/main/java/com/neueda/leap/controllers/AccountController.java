package com.neueda.leap.controllers;

import com.neueda.leap.dtos.AccountResponse;
import com.neueda.leap.exceptions.AccountNotFoundException;
import com.neueda.leap.model.Account;
import com.neueda.leap.repositories.AccountRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {
    private final AccountRepository accountRepository;

    public AccountController(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @GetMapping
    public ResponseEntity<List<AccountResponse>> getAllAccounts() {
        List<Account> accounts = accountRepository.findAll();
        List<AccountResponse> responses = accounts.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
        return ResponseEntity.ok(mapToResponse(account));
    }

    private AccountResponse mapToResponse(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getHolderName(),
                account.getCashBalance(),
                account.getStatus(),
                account.getLastUpdated().toEpochMilli()  // Convert Instant to Long (milliseconds)
        );
    }
}