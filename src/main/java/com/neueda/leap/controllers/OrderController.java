package com.neueda.leap.controllers;

import com.neueda.leap.dtos.OrderResponse;
import com.neueda.leap.dtos.OrderHistoryResponse;
import com.neueda.leap.dtos.PlaceOrderRequest;
import com.neueda.leap.dtos.PlaceOrderResponse;
import com.neueda.leap.dtos.TradeEventResponse;
import com.neueda.leap.exceptions.AccountNotFoundException;
import com.neueda.leap.exceptions.DuplicateOrderException;
import com.neueda.leap.exceptions.InstrumentNotFoundException;
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
     * An unknown account or instrument fails fast (404) and nothing is stored,
     * since those can never succeed later.
     * 
     * @param request the order placement request with validation
     * @return 202 ACCEPTED with the order ID for tracking
     * @throws AccessDeniedException       if the caller does not own the account
     * @throws AccountNotFoundException    if account does not exist
     * @throws InstrumentNotFoundException if instrument does not exist
     * @throws DuplicateOrderException     if the idempotency key was already used
     */
    @PostMapping
    public ResponseEntity<PlaceOrderResponse> placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
        // Checked first, so a non-owner cannot probe which account IDs exist
        accessGuard.checkAccess(request.accountId());
        Order order = orderService.submitOrder(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(PlaceOrderResponse.accepted(order.getId()));
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getAllOrders() {
        // Admins see every order (monitoring of system order flow); other users
        // see only the orders on their own account
        List<Order> orders = accessGuard.isAdmin()
                ? orderService.findAllOrders()
                : accessGuard.currentAccountId().map(orderService::getOrdersByAccountId).orElse(List.of());
        return ResponseEntity.ok(orders.stream().map(this::mapToResponse).toList());
    }

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
    @GetMapping("/{orderId}/events")
    public ResponseEntity<List<TradeEventResponse>> getOrderEvents(@PathVariable UUID orderId) {
        accessGuard.checkAccess(orderService.getOrder(orderId).getAccountId());
        return ResponseEntity.ok(auditService.eventsForOrder(orderId).stream()
                .map(event -> new TradeEventResponse(event.getEventId(), event.getPreviousStatus(),
                        event.getNewStatus(), event.getReason(), event.getReceivedOn()))
                .toList());
    }

    /** The archived copy of a cancelled order */
    @GetMapping("/history/{orderId}")
    public ResponseEntity<OrderHistoryResponse> getOrderHistory(@PathVariable UUID orderId) {
        OrderHistory history = orderService.getOrderHistory(orderId);
        accessGuard.checkAccess(history.getAccountId());
        return ResponseEntity.ok(mapHistoryToResponse(history));
    }

    /** Archived cancelled orders for an account; works even for closed accounts */
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
