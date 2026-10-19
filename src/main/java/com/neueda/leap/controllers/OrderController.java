package com.neueda.leap.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import com.neueda.leap.dtos.OrderResponse;
import com.neueda.leap.dtos.OrderHistoryResponse;
import com.neueda.leap.dtos.PlaceOrderRequest;
import com.neueda.leap.dtos.PlaceOrderResponse;
import com.neueda.leap.dtos.TradeEventResponse;
import com.neueda.leap.exceptions.AccountNotActiveException;
import com.neueda.leap.exceptions.AccountNotFoundException;
import com.neueda.leap.exceptions.DuplicateOrderException;
import com.neueda.leap.exceptions.InstrumentNotFoundException;
import com.neueda.leap.exceptions.LimitNotMarketableException;
import com.neueda.leap.exceptions.PriceUnavailableException;
import com.neueda.leap.model.Order;
import com.neueda.leap.model.OrderHistory;
import com.neueda.leap.security.AccountAccessGuard;
import com.neueda.leap.services.OrderService;
import com.neueda.leap.services.TradeEventAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;
    private final AccountAccessGuard accessGuard;
    private final TradeEventAuditService auditService;

    /**
     * Places an order asynchronously.
     * 
     * The order is stored as NEW and published to Kafka, and 202 ACCEPTED is
     * returned straight away. The Trade Executor works it from there; poll
     * GET /api/v1/orders/{orderId} for FILLED or REJECTED. Failures after this
     * point (insufficient funds, etc.) are captured in the DLQ for review and
     * replay.
     * 
     * If the request has no price, the order is priced at the latest live
     * market price, which is returned in the response.
     * 
     * An unknown account or instrument (404), an account that is not ACTIVE
     * (403 ACC-403) or an instrument that is not tradable (404 INS-404) fails
     * fast and nothing is stored, since those can never succeed later.
     * 
     * @param request the order placement request with validation
     * @return 202 ACCEPTED with the order ID for tracking
     * @throws AccessDeniedException       if the caller does not own the account
     * @throws AccountNotFoundException    if account does not exist
     * @throws AccountNotActiveException   if the account is SUSPENDED or CLOSED
     * @throws InstrumentNotFoundException if instrument does not exist or is not tradable
     * @throws DuplicateOrderException     if the idempotency key was already used
     * @throws PriceUnavailableException   if no price was given and no live price is available
     * @throws LimitNotMarketableException if the limit would not fill at the live price
     */
    @Operation(summary = "Place an order", description = "Checks the account and instrument, stores the order as NEW and queues it for the Trade Executor. Poll GET /api/v1/orders/{orderId} until it is FILLED or REJECTED; funds and holdings are checked then. Without a price the order is priced at the live market price. Limit orders are immediate-or-cancel: when a live price is available, a BUY limit below it or a SELL limit above it is refused with ORD-422; a marketable order fills at the market price, never worse than its limit.")
    @ApiResponse(responseCode = "202", description = "Accepted for execution")
    @ApiResponse(responseCode = "403", description = "AUTH-403 The account is not the caller's; ACC-403 the account is not ACTIVE")
    @ApiResponse(responseCode = "404", description = "ACC-404 Account not found; INS-404 instrument not found or not tradable")
    @ApiResponse(responseCode = "409", description = "ORD-409 The idempotency key was already used")
    @ApiResponse(responseCode = "422", description = "VAL-422 Invalid input; ORD-422 the limit would not fill at the live price (a BUY limit below it or a SELL limit above it)")
    @ApiResponse(responseCode = "503", description = "PRC-503 No price given and no live price; SYS-503 the order could not be queued (it is stored as REJECTED)")
    @PostMapping
    public ResponseEntity<PlaceOrderResponse> placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
        // Checked first, so a non-owner cannot probe which account IDs exist
        accessGuard.checkAccess(request.accountId());
        Order order = orderService.submitOrder(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(PlaceOrderResponse.accepted(order.getId(), order.getPrice()));
    }

    @Operation(summary = "List orders", description = "Support gets every order; other users get the orders on their own account.")
    @GetMapping
    public ResponseEntity<List<OrderResponse>> getAllOrders() {
        // Support sees every order, to help traders; other users see only the
        // orders on their own account
        List<Order> orders = accessGuard.isSupport()
                ? orderService.findAllOrders()
                : accessGuard.currentAccountId().map(orderService::getOrdersByAccountId).orElse(List.of());
        return ResponseEntity.ok(orders.stream().map(this::mapToResponse).toList());
    }

    @Operation(summary = "Get an order")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "403", description = "AUTH-403 The account is not the caller's")
    @ApiResponse(responseCode = "404", description = "ORD-404 Order not found")
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID orderId) {
        Order order = orderService.getOrder(orderId);
        accessGuard.checkAccess(order.getAccountId());
        return ResponseEntity.ok(mapToResponse(order));
    }

    /**
     * Cancels a NEW order and returns it with its updated status (CANCELLED).
     * A FILLED, REJECTED or already CANCELLED order gets 409 ORD-409.
     */
    @Operation(summary = "Cancel a working order", description = "Only NEW orders can be cancelled; returns the order, now CANCELLED.")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "403", description = "AUTH-403 The account is not the caller's")
    @ApiResponse(responseCode = "404", description = "ORD-404 Order not found")
    @ApiResponse(responseCode = "409", description = "ORD-409 The order is not NEW (already filled, rejected or cancelled)")
    @DeleteMapping("/{orderId}")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable UUID orderId) {
        // Looked up first so the ownership check runs before any change
        accessGuard.checkAccess(orderService.getOrder(orderId).getAccountId());
        Order cancelled = orderService.cancelOrder(orderId);
        return ResponseEntity.ok(mapToResponse(cancelled));
    }

    /**
     * The order's lifecycle (e.g. NEW -> FILLED), from the audit trail of the
     * trade-events topic, oldest first.
     */
    @Operation(summary = "Lifecycle events of an order", description = "From the trade-events audit trail, oldest first.")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "403", description = "AUTH-403 The account is not the caller's")
    @ApiResponse(responseCode = "404", description = "ORD-404 Order not found")
    @GetMapping("/{orderId}/events")
    public ResponseEntity<List<TradeEventResponse>> getOrderEvents(@PathVariable UUID orderId) {
        accessGuard.checkAccess(orderService.getOrder(orderId).getAccountId());
        return ResponseEntity.ok(auditService.eventsForOrder(orderId).stream()
                .map(event -> new TradeEventResponse(event.getEventId(), event.getPreviousStatus(),
                        event.getNewStatus(), event.getReason(), event.getReceivedOn()))
                .toList());
    }

    /** The archived copy of a cancelled order */
    @Operation(summary = "Archived copy of a cancelled order")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "403", description = "AUTH-403 The account is not the caller's")
    @ApiResponse(responseCode = "404", description = "ORD-404 No archived copy of this order")
    @GetMapping("/history/{orderId}")
    public ResponseEntity<OrderHistoryResponse> getOrderHistory(@PathVariable UUID orderId) {
        OrderHistory history = orderService.getOrderHistory(orderId);
        accessGuard.checkAccess(history.getAccountId());
        return ResponseEntity.ok(mapHistoryToResponse(history));
    }

    /** Archived cancelled orders for an account; works even for closed accounts */
    @Operation(summary = "Cancelled orders of an account", description = "Works for closed accounts too.")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "403", description = "AUTH-403 The account is not the caller's")
    @GetMapping("/{accountId}/history")
    public ResponseEntity<List<OrderHistoryResponse>> getAccountOrderHistory(@PathVariable Long accountId) {
        accessGuard.checkAccess(accountId);
        return ResponseEntity.ok(orderService.getAccountOrderHistory(accountId).stream()
                .map(this::mapHistoryToResponse)
                .toList());
    }

    private OrderResponse mapToResponse(Order order) {
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

    private OrderHistoryResponse mapHistoryToResponse(OrderHistory history) {
        // Converts OrderHistory entity to response
        return new OrderHistoryResponse(
                history.getOrderId(),
                history.getAccountId(),
                history.getSymbol(),
                history.getSide(),
                history.getQuantity(),
                history.getPrice(),
                history.getStatus(),
                history.getOrderCreatedOn(),
                history.getDeletedOn());
    }
}
