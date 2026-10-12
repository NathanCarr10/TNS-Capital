package com.neueda.leap.controllers;

import com.neueda.leap.dtos.PositionResponse;
import com.neueda.leap.exceptions.AccountNotFoundException;
import com.neueda.leap.model.Position;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.PositionRepository;
import com.neueda.leap.security.AccountAccessGuard;
import com.neueda.leap.services.PositionValuation;
import com.neueda.leap.utils.InputNormalizer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@RestController
@RequestMapping("/api/v1/positions")
public class PositionController {
        private final PositionRepository positionRepository;
        private final AccountRepository accountRepository;
        private final AccountAccessGuard accessGuard;
        private final PositionValuation positionValuation;

        public PositionController(PositionRepository positionRepository,
                        AccountRepository accountRepository, AccountAccessGuard accessGuard,
                        PositionValuation positionValuation) {
                this.positionRepository = positionRepository;
                this.accountRepository = accountRepository;
                this.accessGuard = accessGuard;
                this.positionValuation = positionValuation;
        }

        @SuppressWarnings("null")
        @GetMapping("/{accountId}")
        public ResponseEntity<List<PositionResponse>> getAccountPositions(@PathVariable @NotNull Long accountId) {
                // Validates account exists before querying positions; prevents exposing
                // holdings for non-existent accounts
                accessGuard.checkAccess(accountId);
                accountRepository.findById(accountId)
                                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));

                List<Position> positions = positionRepository.findByAccountId(accountId);
                return ResponseEntity.ok(positionValuation.toResponses(positions));
        }

        @SuppressWarnings("null")
        @GetMapping("/{accountId}/{symbol}")
        public ResponseEntity<PositionResponse> getPosition(@PathVariable @NotNull Long accountId,
                        @PathVariable String symbol) {
                // Validates account exists first; normalizes symbol for consistent lookup
                accessGuard.checkAccess(accountId);
                accountRepository.findById(accountId)
                                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));

                String normalizedSymbol = InputNormalizer.normalize(symbol);
                Position position = positionRepository.findByAccountIdAndSymbol(accountId, normalizedSymbol)
                                .orElseThrow(() -> new com.neueda.leap.exceptions.PositionNotFoundException(
                                                "Position not found: " + accountId + " " + symbol));
                return ResponseEntity.ok(positionValuation.toResponse(position));
        }
}