package com.neueda.leap.controllers;

import com.neueda.leap.dtos.AccountResponse;
import com.neueda.leap.dtos.CreateAccountRequest;
import com.neueda.leap.dtos.UpdateAccountRequest;
import com.neueda.leap.dtos.UpdateAccountStatusRequest;
import com.neueda.leap.dtos.OrderResponse;
import com.neueda.leap.dtos.PositionResponse;

import com.neueda.leap.model.Account;
import com.neueda.leap.model.Order;
import com.neueda.leap.model.Position;
import com.neueda.leap.security.AccountAccess;
import com.neueda.leap.services.AccountService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Admins (ROLE_ADMIN) can see and manage every account. Customers can only
 * read/update accounts they own; opening, closing and approving accounts
 * (status changes) is admin-only - see SecurityConfig.
 */
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {
        private static final String ADMIN_OR_OWNER = "hasRole('ADMIN') or @accountAccess.ownsAccount(authentication, #accountId)";

        private final AccountService accountService;
        private final AccountAccess accountAccess;

        public AccountController(AccountService accountService, AccountAccess accountAccess) {
                this.accountService = accountService;
                this.accountAccess = accountAccess;
        }

        @GetMapping
        public ResponseEntity<List<AccountResponse>> getAllAccounts(Authentication authentication) {
                // Admins see every account; customers only see their own (empty if they have none)
                List<Account> accounts = accountAccess.isAdmin(authentication)
                                ? accountService.getAllAccounts()
                                : accountAccess.ownedAccounts(authentication);
                List<AccountResponse> responses = accounts.stream()
                                .map(this::mapToResponse)
                                .collect(Collectors.toList());
                return ResponseEntity.ok(responses);
        }

        @PostMapping
        public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
                Account savedAccount = accountService.createAccount(request.accountNumber(), request.holderName(),
                                request.cashBalance(), request.ownerUsername());
                return ResponseEntity.status(HttpStatus.CREATED).body(mapToResponse(savedAccount));
        }

        @PatchMapping("/{accountId}")
        @PreAuthorize(ADMIN_OR_OWNER)
        public ResponseEntity<AccountResponse> updateAccount(@PathVariable @NotNull Long accountId,
                        @Valid @RequestBody UpdateAccountRequest request) {
                Account updatedAccount = accountService.updateHolderName(accountId, request.holderName());
                return ResponseEntity.ok(mapToResponse(updatedAccount));
        }

        @PatchMapping("/{accountId}/status")
        @PreAuthorize("hasRole('ADMIN')")
        public ResponseEntity<AccountResponse> updateAccountStatus(@PathVariable @NotNull Long accountId,
                        @Valid @RequestBody UpdateAccountStatusRequest request) {
                // Admin approves (ACTIVE), suspends or closes an account; only ACTIVE accounts can trade
                return ResponseEntity.ok(mapToResponse(accountService.updateStatus(accountId, request.status())));
        }

        /**
         * Closes the account (status CLOSED). The record is kept for the audit
         * trail; closed accounts can no longer trade.
         */
        @DeleteMapping("/{accountId}")
        @PreAuthorize("hasRole('ADMIN')")
        public ResponseEntity<Void> deleteAccount(@PathVariable @NotNull Long accountId) {
                accountService.closeAccount(accountId);
                return ResponseEntity.noContent().build();
        }

        @GetMapping("/{accountId}")
        @PreAuthorize(ADMIN_OR_OWNER)
        public ResponseEntity<AccountResponse> getAccount(@PathVariable @NotNull Long accountId) {
                return ResponseEntity.ok(mapToResponse(accountService.getAccountById(accountId)));
        }

        @GetMapping("/{accountId}/balance")
        @PreAuthorize(ADMIN_OR_OWNER)
        public ResponseEntity<BalanceResponse> getAccountBalance(@PathVariable @NotNull Long accountId) {
                return ResponseEntity.ok(new BalanceResponse(accountService.getCashBalance(accountId)));
        }

        @GetMapping("/{accountId}/positions")
        @PreAuthorize(ADMIN_OR_OWNER)
        public ResponseEntity<List<PositionResponse>> getAccountPositions(@PathVariable @NotNull Long accountId) {
                List<PositionResponse> responses = accountService.getPositions(accountId).stream()
                                .map(this::mapPositionToResponse)
                                .collect(Collectors.toList());
                return ResponseEntity.ok(responses);
        }

        @GetMapping("/{accountId}/orders")
        @PreAuthorize(ADMIN_OR_OWNER)
        public ResponseEntity<List<OrderResponse>> getAccountOrders(@PathVariable @NotNull Long accountId) {
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
                                account.getLastUpdated().toEpochMilli(), // Convert Instant to Long (milliseconds)
                                account.getOwnerUsername());
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
                                order.getCreatedOn());
        }

        /**
         * Simple response wrapper for account balance query.
         */
        public record BalanceResponse(BigDecimal balance) {
        }
}