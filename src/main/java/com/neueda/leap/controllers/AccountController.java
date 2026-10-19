package com.neueda.leap.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import com.neueda.leap.dtos.AccountResponse;
import com.neueda.leap.dtos.CashTransactionRequest;
import com.neueda.leap.dtos.CreateAccountRequest;
import com.neueda.leap.dtos.UpdateAccountRequest;
import com.neueda.leap.dtos.UpdateAccountStatusRequest;
import com.neueda.leap.dtos.OrderResponse;
import com.neueda.leap.dtos.PositionResponse;

import com.neueda.leap.model.Account;
import com.neueda.leap.model.Order;
import com.neueda.leap.security.AccountAccessGuard;
import com.neueda.leap.services.AccountService;
import com.neueda.leap.services.PositionValuationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * Account endpoints. Admins can use every account; other users only the
 * account opened for them at registration (see AccountAccessGuard).
 */
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {
        private final AccountService accountService;
        private final AccountAccessGuard accessGuard;
        private final PositionValuationService valuationService;

        public AccountController(AccountService accountService, AccountAccessGuard accessGuard,
                        PositionValuationService valuationService) {
                this.accountService = accountService;
                this.accessGuard = accessGuard;
                this.valuationService = valuationService;
        }

        /**
         * Support gets every account; other users get a list holding only their own.
         */
        @Operation(summary = "List accounts", description = "Support gets every account; other users get a list holding only their own.")
        @GetMapping
        public ResponseEntity<List<AccountResponse>> getAllAccounts() {
                List<Account> accounts = accessGuard.isSupport()
                                ? accountService.getAllAccounts()
                                : accessGuard.currentAccountId()
                                                .flatMap(accountService::findAccountById)
                                                .map(List::of)
                                                .orElse(List.of());
                List<AccountResponse> responses = accounts.stream()
                                .map(this::mapToResponse)
                                .toList();
                return ResponseEntity.ok(responses);
        }

        /**
         * Users get their account when they register with the auth service, so
         * opening further accounts is a support task.
         */
        @Operation(summary = "Open an account", description = "Support only: users get their account when they register.")
        @ApiResponse(responseCode = "201", description = "Account opened")
        @ApiResponse(responseCode = "403", description = "AUTH-403 Support only")
        @ApiResponse(responseCode = "409", description = "ACC-409 The account number is taken")
        @PostMapping
        @PreAuthorize("hasRole('SUPPORT')")
        public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
                Account savedAccount = accountService.createAccount(request.accountNumber(), request.holderName(),
                                request.cashBalance());
                return ResponseEntity.status(HttpStatus.CREATED).body(mapToResponse(savedAccount));
        }

        @Operation(summary = "Change the holder name")
        @ApiResponse(responseCode = "200", description = "OK")
        @ApiResponse(responseCode = "403", description = "AUTH-403 The account is not the caller's")
        @ApiResponse(responseCode = "404", description = "ACC-404 Account not found")
        @PatchMapping("/{accountId}")
        public ResponseEntity<AccountResponse> updateAccount(@PathVariable @NotNull Long accountId,
                        @Valid @RequestBody UpdateAccountRequest request) {
                accessGuard.checkAccess(accountId);
                Account updatedAccount = accountService.updateHolderName(accountId, request.holderName());
                return ResponseEntity.ok(mapToResponse(updatedAccount));
        }

        /**
         * Suspends or reactivates an account; a support task.
         */
        @Operation(summary = "Suspend or reactivate an account", description = "Support only. Closing goes through DELETE.")
        @ApiResponse(responseCode = "200", description = "OK")
        @ApiResponse(responseCode = "403", description = "AUTH-403 Support only")
        @ApiResponse(responseCode = "404", description = "ACC-404 Account not found")
        @ApiResponse(responseCode = "422", description = "VAL-422 Invalid input, or a status other than ACTIVE or SUSPENDED")
        @PutMapping("/{accountId}/status")
        @PreAuthorize("hasRole('SUPPORT')")
        public ResponseEntity<AccountResponse> updateAccountStatus(@PathVariable @NotNull Long accountId,
                        @Valid @RequestBody UpdateAccountStatusRequest request) {
                return ResponseEntity.ok(mapToResponse(accountService.updateStatus(accountId, request.status())));
        }

        /**
         * Closes the account (status CLOSED). The record is kept for the audit
         * trail; closed accounts can no longer trade. Refused with 409 ACC-409
         * while the account has working orders, holdings or cash.
         */
        @Operation(summary = "Close an account", description = "Sets the status to CLOSED and keeps the record for the audit trail. The account must be empty first.")
        @ApiResponse(responseCode = "204", description = "Account closed")
        @ApiResponse(responseCode = "403", description = "AUTH-403 The account is not the caller's")
        @ApiResponse(responseCode = "404", description = "ACC-404 Account not found")
        @ApiResponse(responseCode = "409", description = "ACC-409 The account has working orders, holdings or cash")
        @DeleteMapping("/{accountId}")
        public ResponseEntity<Void> deleteAccount(@PathVariable @NotNull Long accountId) {
                accessGuard.checkAccess(accountId);
                accountService.closeAccount(accountId);
                return ResponseEntity.noContent().build();
        }

        @Operation(summary = "Get an account")
        @ApiResponse(responseCode = "200", description = "OK")
        @ApiResponse(responseCode = "403", description = "AUTH-403 The account is not the caller's")
        @ApiResponse(responseCode = "404", description = "ACC-404 Account not found")
        @GetMapping("/{accountId}")
        public ResponseEntity<AccountResponse> getAccount(@PathVariable @NotNull Long accountId) {
                accessGuard.checkAccess(accountId);
                return ResponseEntity.ok(mapToResponse(accountService.getAccountById(accountId)));
        }

        @Operation(summary = "Cash balance of an account")
        @ApiResponse(responseCode = "200", description = "OK")
        @ApiResponse(responseCode = "403", description = "AUTH-403 The account is not the caller's")
        @ApiResponse(responseCode = "404", description = "ACC-404 Account not found")
        @GetMapping("/{accountId}/balance")
        public ResponseEntity<BalanceResponse> getAccountBalance(@PathVariable @NotNull Long accountId) {
                accessGuard.checkAccess(accountId);
                Account account = accountService.getAccountById(accountId);
                return ResponseEntity.ok(new BalanceResponse(account.getCashBalance(), account.getReservedCash(),
                                account.getAvailableCash()));
        }

        /**
         * Credits cash to the account, e.g. to fund a new account before buying.
         */
        @Operation(summary = "Deposit cash")
        @ApiResponse(responseCode = "200", description = "OK")
        @ApiResponse(responseCode = "403", description = "AUTH-403 The account is not the caller's; ACC-403 the account is not ACTIVE")
        @ApiResponse(responseCode = "404", description = "ACC-404 Account not found")
        @ApiResponse(responseCode = "409", description = "SYS-409 The account was changed by another request at the same time; try again")
        @PostMapping("/{accountId}/deposit")
        public ResponseEntity<AccountResponse> deposit(@PathVariable @NotNull Long accountId,
                        @Valid @RequestBody CashTransactionRequest request) {
                accessGuard.checkAccess(accountId);
                return ResponseEntity.ok(mapToResponse(accountService.deposit(accountId, request.amount())));
        }

        /**
         * Debits cash from the account; fails with ORD-400 if the available cash is too low
     * (cash reserved for open orders cannot be withdrawn).
         */
        @Operation(summary = "Withdraw cash")
        @ApiResponse(responseCode = "200", description = "OK")
        @ApiResponse(responseCode = "400", description = "ORD-400 Insufficient available funds (cash reserved for open orders cannot be withdrawn)")
        @ApiResponse(responseCode = "403", description = "AUTH-403 The account is not the caller's; ACC-403 the account is not ACTIVE")
        @ApiResponse(responseCode = "404", description = "ACC-404 Account not found")
        @ApiResponse(responseCode = "409", description = "SYS-409 The account was changed by another request at the same time; try again")
        @PostMapping("/{accountId}/withdraw")
        public ResponseEntity<AccountResponse> withdraw(@PathVariable @NotNull Long accountId,
                        @Valid @RequestBody CashTransactionRequest request) {
                accessGuard.checkAccess(accountId);
                return ResponseEntity.ok(mapToResponse(accountService.withdraw(accountId, request.amount())));
        }

        @Operation(summary = "Holdings of an account", description = "Valued at the live market price when one is available.")
        @ApiResponse(responseCode = "200", description = "OK")
        @ApiResponse(responseCode = "403", description = "AUTH-403 The account is not the caller's")
        @ApiResponse(responseCode = "404", description = "ACC-404 Account not found")
        @GetMapping("/{accountId}/positions")
        public ResponseEntity<List<PositionResponse>> getAccountPositions(@PathVariable @NotNull Long accountId) {
                accessGuard.checkAccess(accountId);
                return ResponseEntity.ok(valuationService.toResponses(accountService.getPositions(accountId)));
        }

        @Operation(summary = "Order history of an account")
        @ApiResponse(responseCode = "200", description = "OK")
        @ApiResponse(responseCode = "403", description = "AUTH-403 The account is not the caller's")
        @ApiResponse(responseCode = "404", description = "ACC-404 Account not found")
        @GetMapping("/{accountId}/orders")
        public ResponseEntity<List<OrderResponse>> getAccountOrders(@PathVariable @NotNull Long accountId) {
                accessGuard.checkAccess(accountId);
                List<OrderResponse> responses = accountService.getOrders(accountId).stream()
                                .map(this::mapOrderToResponse)
                                .toList();
                return ResponseEntity.ok(responses);
        }

        private AccountResponse mapToResponse(Account account) {
                return new AccountResponse(
                                account.getId(),
                                account.getAccountNumber(),
                                account.getHolderName(),
                                account.getCashBalance(),
                                account.getReservedCash(),
                                account.getAvailableCash(),
                                account.getStatus(),
                                account.getOpenedOn(),
                                account.getLastUpdated()
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
         * Cash balance of an account.
         *
         * @param balance       all the cash in the account
         * @param reservedCash  cash held for open BUY orders
         * @param availableCash cash that can be traded or withdrawn: balance - reservedCash
         */
        public record BalanceResponse(
                        @Schema(requiredMode = REQUIRED) BigDecimal balance,
                        @Schema(requiredMode = REQUIRED) BigDecimal reservedCash,
                        @Schema(requiredMode = REQUIRED) BigDecimal availableCash) {
        }
}