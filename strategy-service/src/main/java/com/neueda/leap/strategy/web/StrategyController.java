package com.neueda.leap.strategy.web;

import com.neueda.leap.strategy.backtest.BacktestResult;
import com.neueda.leap.strategy.performance.PerformanceResponse;
import com.neueda.leap.strategy.service.CallerResolver;
import com.neueda.leap.strategy.service.StrategyService;
import com.neueda.leap.strategy.web.dto.BacktestRequest;
import com.neueda.leap.strategy.web.dto.CreateStrategyRequest;
import com.neueda.leap.strategy.web.dto.SignalResponse;
import com.neueda.leap.strategy.web.dto.StatusChangeRequest;
import com.neueda.leap.strategy.web.dto.StrategyResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Strategy Lab's API. Traders see and change only the strategies on their
 * own account; admins see every strategy and change none.
 */
@RestController
@RequestMapping("/api/v1/strategies")
@Tag(name = "strategies", description = "Backtest trading rules and run them on the live price stream")
public class StrategyController {
    private final StrategyService service;
    private final CallerResolver callers;

    public StrategyController(StrategyService service, CallerResolver callers) {
        this.service = Objects.requireNonNull(service);
        this.callers = Objects.requireNonNull(callers);
    }

    @Operation(summary = "List strategies", description = "A trader's own strategies; every strategy for admins. Deleted (archived) ones are left out.")
    @ApiResponse(responseCode = "200", description = "OK")
    @GetMapping
    public List<StrategyResponse> listStrategies(Authentication caller) {
        return service.list(callers.resolve(caller));
    }

    @Operation(summary = "Create a strategy", description = "On the caller's own account, PAUSED until switched on. Missing rule settings take their defaults.")
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiResponse(responseCode = "403", description = "AUTH-403 The caller has no trading account (admins)")
    @ApiResponse(responseCode = "404", description = "INS-404 Instrument not found or not tradable")
    @ApiResponse(responseCode = "422", description = "VAL-422 Invalid input; STR-422 rule settings out of range")
    @PostMapping
    public ResponseEntity<StrategyResponse> createStrategy(@Valid @RequestBody CreateStrategyRequest request,
            Authentication caller) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(callers.resolve(caller), request));
    }

    @Operation(summary = "Get a strategy", description = "With the 1-minute bars collected so far for its symbol, live and replayed.")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "403", description = "AUTH-403 Not on the caller's account")
    @ApiResponse(responseCode = "404", description = "STR-404 Strategy not found")
    @GetMapping("/{id}")
    public StrategyResponse getStrategy(@PathVariable Long id, Authentication caller) {
        return service.get(callers.resolve(caller), id);
    }

    @Operation(summary = "Switch a strategy on or off", description = "ACTIVE: it trades on each 1-minute bar of its symbol. PAUSED: it stops. Up to strategy.max-active-per-account (default 3) strategies per account can be on at a time.")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "403", description = "AUTH-403 Not on the caller's account (admins cannot change strategies)")
    @ApiResponse(responseCode = "404", description = "STR-404 Strategy not found")
    @ApiResponse(responseCode = "409", description = "STR-409 The account already has its maximum of strategies on, or the strategy is deleted")
    @ApiResponse(responseCode = "422", description = "VAL-422 Invalid input")
    @PutMapping("/{id}/status")
    public StrategyResponse changeStrategyStatus(@PathVariable Long id,
            @Valid @RequestBody StatusChangeRequest request, Authentication caller) {
        return service.changeStatus(callers.resolve(caller), id, request.status());
    }

    @Operation(summary = "Delete a strategy", description = "It stops trading and is archived, so its signals stay on record.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "403", description = "AUTH-403 Not on the caller's account")
    @ApiResponse(responseCode = "404", description = "STR-404 Strategy not found")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStrategy(@PathVariable Long id, Authentication caller) {
        service.archive(callers.resolve(caller), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "A strategy's signals", description = "The latest 100, newest first, each with the order it placed and that order's status now.")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "403", description = "AUTH-403 Not on the caller's account")
    @ApiResponse(responseCode = "404", description = "STR-404 Strategy not found")
    @GetMapping("/{id}/signals")
    public List<SignalResponse> listStrategySignals(@PathVariable Long id, Authentication caller) {
        return service.signals(callers.resolve(caller), id);
    }

    @Operation(summary = "Recent signals of all the caller's strategies", description = "Signals since the given time, newest first (at most 100), for notifications. Admins get every strategy's.")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "422", description = "VAL-422 since is missing or not an ISO-8601 time")
    @GetMapping("/activity")
    public List<SignalResponse> listStrategyActivity(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since, Authentication caller) {
        return service.activity(callers.resolve(caller), since);
    }

    @Operation(summary = "Strategy trading compared with the trader's own", description = "From the account's filled orders: those its strategies placed, and the rest. Each side at average cost: trades, bought, sold, realized P&L and the cost of shares still held. Traders get their own account; admins pass accountId.")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "403", description = "AUTH-403 Not the caller's account, or an admin without accountId")
    @GetMapping("/performance")
    public PerformanceResponse getStrategyPerformance(@RequestParam(required = false) Long accountId,
            Authentication caller) {
        return service.performance(callers.resolve(caller), accountId);
    }

    @Operation(summary = "Backtest a rule", description = "Runs the rule over the symbol's daily closes (analytics.fact_daily_prices, default the last five years): long only, all its cash in whole shares on BUY, all sold on SELL, at the day's close, no fees. Compared with buying on the first day and holding.")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "404", description = "PRC-404 Not enough price history for the rule")
    @ApiResponse(responseCode = "422", description = "VAL-422 Invalid input; STR-422 rule settings out of range")
    @PostMapping("/backtests")
    public BacktestResult runBacktest(@Valid @RequestBody BacktestRequest request) {
        return service.backtest(request);
    }
}
