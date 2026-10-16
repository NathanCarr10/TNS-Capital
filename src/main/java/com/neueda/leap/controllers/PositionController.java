package com.neueda.leap.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import com.neueda.leap.dtos.PositionResponse;
import com.neueda.leap.security.AccountAccessGuard;
import com.neueda.leap.services.AccountService;
import com.neueda.leap.services.PositionValuationService;
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
        private final PositionValuationService valuationService;

        public PositionController(AccountService accountService, AccountAccessGuard accessGuard,
                        PositionValuationService valuationService) {
                this.accountService = accountService;
                this.accessGuard = accessGuard;
                this.valuationService = valuationService;
        }

        @Operation(summary = "Holdings of an account", description = "Valued at the live market price when one is available.")
        @ApiResponse(responseCode = "200", description = "OK")
        @ApiResponse(responseCode = "403", description = "AUTH-403 The account is not the caller's")
        @ApiResponse(responseCode = "404", description = "ACC-404 Account not found")
        @GetMapping("/{accountId}")
        public ResponseEntity<List<PositionResponse>> getAccountPositions(@PathVariable @NotNull Long accountId) {
                // Ownership first, so a non-owner cannot probe which account IDs exist
                accessGuard.checkAccess(accountId);
                return ResponseEntity.ok(valuationService.toResponses(accountService.getPositions(accountId)));
        }

        @Operation(summary = "One holding of an account")
        @ApiResponse(responseCode = "200", description = "OK")
        @ApiResponse(responseCode = "403", description = "AUTH-403 The account is not the caller's")
        @ApiResponse(responseCode = "404", description = "ACC-404 Account not found; POS-404 the account holds none of this symbol")
        @GetMapping("/{accountId}/{symbol}")
        public ResponseEntity<PositionResponse> getPosition(@PathVariable @NotNull Long accountId,
                        @PathVariable String symbol) {
                accessGuard.checkAccess(accountId);
                return ResponseEntity.ok(valuationService.toResponse(accountService.getPosition(accountId, symbol)));
        }
}
