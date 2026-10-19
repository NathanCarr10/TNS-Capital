package com.neueda.leap.controllers;

import com.neueda.leap.dtos.StartReplayRequest;
import com.neueda.leap.marketdata.MarketReplayService;
import com.neueda.leap.marketdata.ReplayStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Replays of a past trading day on the market-data topic, for Strategy Lab
 * demos outside market hours. Any signed-in user may start one; only one runs
 * at a time. The strategy service's own token cannot use these endpoints.
 */
@RestController
@RequestMapping("/api/v1/market-data/replays")
@RequiredArgsConstructor
public class MarketReplayController {
    private final MarketReplayService replayService;

    @Operation(summary = "Replay a past trading day", description = "Publishes the day's regular-session 1-minute bars of the symbol on the market-data topic as REPLAY prices, barsPerSecond at a time (default 10), so active strategies on it trade. Orders are still priced at the live market price. One replay at a time.")
    @ApiResponse(responseCode = "202", description = "Replay started")
    @ApiResponse(responseCode = "404", description = "INS-404 Instrument not found or not tradable; MKT-404 no bars that day (weekend or market holiday)")
    @ApiResponse(responseCode = "409", description = "MKT-409 Another replay is running")
    @ApiResponse(responseCode = "422", description = "VAL-422 Invalid input")
    @ApiResponse(responseCode = "503", description = "MKT-503 No market data keys are configured")
    @PostMapping
    public ResponseEntity<ReplayStatus> startReplay(@Valid @RequestBody StartReplayRequest request,
            Authentication caller) {
        ReplayStatus status = replayService.start(request.symbol(), request.date(),
                request.barsPerSecondOrDefault(), caller.getName());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(status);
    }

    @Operation(summary = "The running or last replay")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "204", description = "No replay was started since the API started")
    @GetMapping("/current")
    public ResponseEntity<ReplayStatus> currentReplay() {
        return replayService.current().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "Stop the running replay")
    @ApiResponse(responseCode = "200", description = "Stopped; the bars published so far stay on the topic")
    @ApiResponse(responseCode = "404", description = "MKT-404 No replay is running")
    @DeleteMapping("/current")
    public ResponseEntity<ReplayStatus> stopReplay() {
        return ResponseEntity.ok(replayService.stop());
    }
}
