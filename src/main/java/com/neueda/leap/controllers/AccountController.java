package com.neueda.leap.controllers;

import com.neueda.leap.dtos.AccountResponse;
import com.neueda.leap.dtos.CreateAccountRequest;
import com.neueda.leap.dtos.UpdateAccountRequest;
import com.neueda.leap.dtos.OrderResponse;
import com.neueda.leap.dtos.PositionResponse;

import com.neueda.leap.exceptions.AccountDeletionConflictException;
import com.neueda.leap.exceptions.AccountNotFoundException;
import com.neueda.leap.model.Account;
import com.neueda.leap.model.Order;
import com.neueda.leap.model.Position;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.repositories.PositionRepository;
import com.neueda.leap.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {
        private final AccountRepository accountRepository;
        private final PositionRepository positionRepository;
        private final OrderRepository orderRepository;
        private final Clock clock;

        public AccountController(AccountRepository accountRepository,
                        PositionRepository positionRepository,
                        OrderRepository orderRepository,
                        Clock clock) {
                this.accountRepository = accountRepository;
                this.positionRepository = positionRepository;
                this.orderRepository = orderRepository;
                this.clock = clock;
        }

        @GetMapping
        public ResponseEntity<List<AccountResponse>> getAllAccounts() {
                List<Account> accounts = accountRepository.findAll();
                List<AccountResponse> responses = accounts.stream()
                                .map(this::mapToResponse)
                                .collect(Collectors.toList());
                return ResponseEntity.ok(responses);
        }

        @PostMapping
        public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
                // Creates new account with provided details; Clock ensures consistent timestamp
                Account account = new Account(request.accountNumber(), request.holderName(), request.cashBalance(),
                                clock);
                Account savedAccount = accountRepository.save(account);
                return ResponseEntity.status(HttpStatus.CREATED).body(mapToResponse(savedAccount));
        }

        @SuppressWarnings("null")
        @PatchMapping("/{accountId}")
        public ResponseEntity<AccountResponse> updateAccount(@PathVariable @NotNull Long accountId,
                        @Valid @RequestBody UpdateAccountRequest request) {
                // Retrieves existing account; throws exception if not found to maintain REST
                // consistency
                Account account = accountRepository.findById(accountId)
                                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));

                // Updates only provided fields; supports partial updates via PATCH
                if (request.holderName() != null && !request.holderName().trim().isEmpty()) {
                        account.setHolderName(request.holderName());
                }

                Account updatedAccount = accountRepository.save(account);
                return ResponseEntity.ok(mapToResponse(updatedAccount));
        }

        @SuppressWarnings("null")
        @DeleteMapping("/{accountId}")
        public ResponseEntity<Void> deleteAccount(@PathVariable @NotNull Long accountId) {
                // Validates account exists before deletion; prevents silently ignoring requests
                // for non-existent accounts
                Account account = accountRepository.findById(accountId)
                                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));

                // Checks for active (NEW status) orders; prevents deletion of accounts with
                // pending orders
                List<Order> activeOrders = orderRepository.findByAccountId(accountId).stream()
                                .filter(order -> order.getStatus() == OrderStatus.NEW)
                                .collect(Collectors.toList());

                if (!activeOrders.isEmpty()) {
                        throw new AccountDeletionConflictException(
                                        "Cannot delete account with " + activeOrders.size() + " active order(s)");
                }

                // Deletes account and returns no content
                accountRepository.delete(account);
                return ResponseEntity.noContent().build();
        }

        @SuppressWarnings("null")
        @GetMapping("/{accountId}")
        public ResponseEntity<AccountResponse> getAccount(@PathVariable @NotNull Long accountId) {
                Account account = accountRepository.findById(accountId)
                                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
                return ResponseEntity.ok(mapToResponse(account));
        }

        @SuppressWarnings("null")
        @GetMapping("/{accountId}/balance")
        public ResponseEntity<BalanceResponse> getAccountBalance(@PathVariable @NotNull Long accountId) {
                // Validates account exists before returning balance; prevents exposing
                // non-existent accounts
                Account account = accountRepository.findById(accountId)
                                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
                return ResponseEntity.ok(new BalanceResponse(account.getCashBalance()));
        }

        @SuppressWarnings("null")
        @GetMapping("/{accountId}/positions")
        public ResponseEntity<List<PositionResponse>> getAccountPositions(@PathVariable @NotNull Long accountId) {
                // Validates account exists; queries positions separately to enable flexible
                // retrieval
                accountRepository.findById(accountId)
                                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
                List<Position> positions = positionRepository.findByAccountId(accountId);
                List<PositionResponse> responses = positions.stream()
                                .map(this::mapPositionToResponse)
                                .collect(Collectors.toList());
                return ResponseEntity.ok(responses);
        }

        @SuppressWarnings("null")
        @GetMapping("/{accountId}/orders")
        public ResponseEntity<List<OrderResponse>> getAccountOrders(@PathVariable @NotNull Long accountId) {
                // Validates account exists; queries orders separately to enable filtering and
                // pagination later
                accountRepository.findById(accountId)
                                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
                List<Order> orders = orderRepository.findByAccountId(accountId);
                List<OrderResponse> responses = orders.stream()
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
                                account.getLastUpdated().toEpochMilli() // Convert Instant to Long (milliseconds)
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
                                order.getCreatedOn());
        }

        /**
         * Simple response wrapper for account balance query.
         */
        public record BalanceResponse(BigDecimal balance) {
        }
}