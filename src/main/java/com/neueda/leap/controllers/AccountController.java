package com.neueda.leap.controllers;

import com.neueda.leap.dtos.AccountResponse;
import com.neueda.leap.dtos.CashTransactionRequest;
import com.neueda.leap.dtos.CreateAccountRequest;
import com.neueda.leap.dtos.UpdateAccountRequest;
import com.neueda.leap.dtos.OrderResponse;
import com.neueda.leap.dtos.PositionResponse;

import com.neueda.leap.model.Account;
import com.neueda.leap.model.Order;
import com.neueda.leap.model.Position;
import com.neueda.leap.security.AccountAccessGuard;
import com.neueda.leap.services.AccountService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Account endpoints. Admins can use every account; other users only the
 * account opened for them at registration (see AccountAccessGuard).
 */
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {
        private final AccountService accountService;
        private final AccountAccessGuard accessGuard;

        public AccountController(AccountService accountService, AccountAccessGuard accessGuard) {
                this.accountService = accountService;
                this.accessGuard = accessGuard;
        }

        /**
         * Admins get every account; other users get a list holding only their own.
         */
        @GetMapping
        public ResponseEntity<List<AccountResponse>> getAllAccounts() {
                List<Account> accounts = accessGuard.isAdmin()
                                ? accountService.getAllAccounts()
                                : accessGuard.currentAccountId()
                                                .flatMap(accountService::findAccountById)
                                                .map(List::of)
                                                .orElse(List.of());
                List<AccountResponse> responses = accounts.stream()
                                .map(this::mapToResponse)
                                .collect(Collectors.toList());
                return ResponseEntity.ok(responses);
        }

        /**
         * Users get their account when they register with the auth service, so
         * opening further accounts is an admin task.
         */
        @PostMapping
        @PreAuthorize("hasRole('ADMIN')")
        public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
                Account savedAccount = accountService.createAccount(request.accountNumber(), request.holderName(),
                                request.cashBalance());
                return ResponseEntity.status(HttpStatus.CREATED).body(mapToResponse(savedAccount));
        }

        @PatchMapping("/{accountId}")
        public ResponseEntity<AccountResponse> updateAccount(@PathVariable @NotNull Long accountId,
                        @Valid @RequestBody UpdateAccountRequest request) {
                accessGuard.checkAccess(accountId);
                Account updatedAccount = accountService.updateHolderName(accountId, request.holderName());
                return ResponseEntity.ok(mapToResponse(updatedAccount));
        }

        /**
         * Closes the account (status CLOSED). The record is kept for the audit
         * trail; closed accounts can no longer trade.
         */
        @DeleteMapping("/{accountId}")
        public ResponseEntity<Void> deleteAccount(@PathVariable @NotNull Long accountId) {
                accessGuard.checkAccess(accountId);
                accountService.closeAccount(accountId);
                return ResponseEntity.noContent().build();
        }

        @GetMapping("/{accountId}")
        public ResponseEntity<AccountResponse> getAccount(@PathVariable @NotNull Long accountId) {
                accessGuard.checkAccess(accountId);
                return ResponseEntity.ok(mapToResponse(accountService.getAccountById(accountId)));
        }

        @GetMapping("/{accountId}/balance")
        public ResponseEntity<BalanceResponse> getAccountBalance(@PathVariable @NotNull Long accountId) {
                accessGuard.checkAccess(accountId);
                return ResponseEntity.ok(new BalanceResponse(accountService.getCashBalance(accountId)));
        }

        /**
         * Credits cash to the account, e.g. to fund a new account before buying.
         */
        @PostMapping("/{accountId}/deposit")
        public ResponseEntity<AccountResponse> deposit(@PathVariable @NotNull Long accountId,
                        @Valid @RequestBody CashTransactionRequest request) {
                accessGuard.checkAccess(accountId);
                return ResponseEntity.ok(mapToResponse(accountService.deposit(accountId, request.amount())));
        }

        /**
         * Debits cash from the account; fails with ORD-400 if the balance is too low.
         */
        @PostMapping("/{accountId}/withdraw")
        public ResponseEntity<AccountResponse> withdraw(@PathVariable @NotNull Long accountId,
                        @Valid @RequestBody CashTransactionRequest request) {
                accessGuard.checkAccess(accountId);
                return ResponseEntity.ok(mapToResponse(accountService.withdraw(accountId, request.amount())));
        }

        @GetMapping("/{accountId}/positions")
        public ResponseEntity<List<PositionResponse>> getAccountPositions(@PathVariable @NotNull Long accountId) {
                accessGuard.checkAccess(accountId);
                List<PositionResponse> responses = accountService.getPositions(accountId).stream()
                                .map(this::mapPositionToResponse)
                                .collect(Collectors.toList());
                return ResponseEntity.ok(responses);
        }

        @GetMapping("/{accountId}/orders")
        public ResponseEntity<List<OrderResponse>> getAccountOrders(@PathVariable @NotNull Long accountId) {
                accessGuard.checkAccess(accountId);
                List<OrderResponse> responses = accountService.getOrders(accountId).stream()
                                .map(this::mapOrderToResponse)
                                .collect(Collectors.toList());
                return ResponseEntity.ok(responses);
        }

        private AccountResponse mapToResponse(Account account) {
                return new AccountResponse(
                                account.getId(),
                                account.getAccountNumber(),
                                account.getHolderName(),
                                account.getCashBalance(),
                                account.getStatus(),
                                account.getLastUpdated()
                );
        }

        private PositionResponse mapPositionToResponse(Position position) {
                // Maps Position entity to response; uses zero marketValue since real-time
                // pricing unavailable in this context
                return new PositionResponse(
                                position.getAccountId(),
                                position.getSymbol(),
                                position.getQuantity(),
                                position.getAverageCost(),
                                BigDecimal.ZERO // Market value requires current instrument price; placeholder for now
                );
        }

        private OrderResponse mapOrderToResponse(Order order) {
                // Converts Order entity to response; UUID to UUID, Instant to Instant for
                // proper REST contract
                return new OrderResponse(
                                order.getId(),
                                order.getAccountId(),
                                order.getSymbol(),
                                order.getSide(),
                                order.getQuantity(),
                                order.getPrice(),
                                order.getStatus(),
                                order.getCreatedOn(),
                                OrderResponse.statusReasonFor(order.getStatus(), order.getStatusReason()));
        }

        /**
         * Simple response wrapper for account balance query.
         */
        public record BalanceResponse(BigDecimal balance) {
        }
}