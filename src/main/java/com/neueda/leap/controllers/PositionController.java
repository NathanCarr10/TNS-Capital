package com.neueda.leap.controllers;

import com.neueda.leap.dtos.PositionResponse;
import com.neueda.leap.security.AccountAccessGuard;
import com.neueda.leap.services.AccountService;
import com.neueda.leap.services.PositionValuation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Holdings, valued at the instruments' latest prices. GET /{accountId} returns
 * the same list as GET /api/v1/accounts/{accountId}/positions.
 */
@RestController
@RequestMapping("/api/v1/positions")
public class PositionController {
        private final AccountService accountService;
        private final AccountAccessGuard accessGuard;
        private final PositionValuation positionValuation;

        public PositionController(AccountService accountService, AccountAccessGuard accessGuard,
                        PositionValuation positionValuation) {
                this.accountService = accountService;
                this.accessGuard = accessGuard;
                this.positionValuation = positionValuation;
        }

        @GetMapping("/{accountId}")
        public ResponseEntity<List<PositionResponse>> getAccountPositions(@PathVariable @NotNull Long accountId) {
                // Ownership first, so a non-owner cannot probe which account IDs exist
                accessGuard.checkAccess(accountId);
                return ResponseEntity.ok(positionValuation.toResponses(accountService.getPositions(accountId)));
        }

        @GetMapping("/{accountId}/{symbol}")
        public ResponseEntity<PositionResponse> getPosition(@PathVariable @NotNull Long accountId,
                        @PathVariable String symbol) {
                accessGuard.checkAccess(accountId);
                return ResponseEntity.ok(positionValuation.toResponse(accountService.getPosition(accountId, symbol)));
        }
}
