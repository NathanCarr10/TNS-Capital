package com.neueda.leap.controllers;

import com.neueda.leap.dtos.PositionResponse;
import com.neueda.leap.exceptions.AccountNotFoundException;
import com.neueda.leap.model.Position;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.PositionRepository;
import com.neueda.leap.utils.InputNormalizer;
import com.neueda.leap.exceptions.PositionNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/positions")
public class PositionController {
        private final PositionRepository positionRepository;
        private final AccountRepository accountRepository;

        public PositionController(PositionRepository positionRepository,
                        AccountRepository accountRepository) {
                this.positionRepository = positionRepository;
                this.accountRepository = accountRepository;
        }

        @GetMapping("/{accountId}")
        public ResponseEntity<List<PositionResponse>> getAccountPositions(@PathVariable Long accountId) {
                // Validates account exists before querying positions; prevents exposing
                // holdings for non-existent accounts
                accountRepository.findById(accountId)
                                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));

    @GetMapping("/{accountId}/{symbol}")
    public ResponseEntity<PositionResponse> getPosition(@PathVariable Long accountId,
                                                        @PathVariable String symbol) {
        // Validates account exists first; normalizes symbol for consistent lookup
        accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
        
        String normalizedSymbol = InputNormalizer.normalize(symbol);
        Position position = positionRepository.findByAccountIdAndSymbol(accountId, normalizedSymbol)
                .orElseThrow(() -> new com.neueda.leap.exceptions.PositionNotFoundException("Position not found: " + accountId + " " + symbol));
        return ResponseEntity.ok(mapToResponse(position));
    }

        @GetMapping("/{accountId}/{symbol}")
        public ResponseEntity<PositionResponse> getPosition(@PathVariable Long accountId,
                        @PathVariable String symbol) {
                // Validates account exists first; normalizes symbol for consistent lookup
                accountRepository.findById(accountId)
                                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));

                String normalizedSymbol = InputNormalizer.normalize(symbol);
                Position position = positionRepository.findByAccountIdAndSymbol(accountId, normalizedSymbol)
                                .orElseThrow(() -> new PositionNotFoundException(
                                                "Position not found: " + accountId + " " + symbol));
                return ResponseEntity.ok(mapToResponse(position));
        }

        private PositionResponse mapToResponse(Position position) {
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
}