package com.neueda.leap.controllers;

import com.neueda.leap.dtos.AccountResponse;
import com.neueda.leap.dtos.OrderResponse;
import com.neueda.leap.dtos.PositionResponse;

import com.neueda.leap.exceptions.AccountNotFoundException;
import com.neueda.leap.model.Account;
import com.neueda.leap.model.Order;
import com.neueda.leap.model.Position;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.repositories.PositionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {
        private final AccountRepository accountRepository;
        private final PositionRepository positionRepository;
        private final OrderRepository orderRepository;

        public AccountController(AccountRepository accountRepository,
                        PositionRepository positionRepository,
                        OrderRepository orderRepository) {
                this.accountRepository = accountRepository;
                this.positionRepository = positionRepository;
                this.orderRepository = orderRepository;
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

        @GetMapping("/{accountId}/balance")
        public ResponseEntity<BalanceResponse> getAccountBalance(@PathVariable Long accountId) {
                // Validates account exists before returning balance; prevents exposing
                // non-existent accounts
                Account account = accountRepository.findById(accountId)
                                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
                return ResponseEntity.ok(new BalanceResponse(account.getCashBalance()));
        }

        @GetMapping("/{accountId}/positions")
        public ResponseEntity<List<PositionResponse>> getAccountPositions(@PathVariable Long accountId) {
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

        @GetMapping("/{accountId}/orders")
        public ResponseEntity<List<OrderResponse>> getAccountOrders(@PathVariable Long accountId) {
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