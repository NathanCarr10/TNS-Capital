package com.neueda.leap.controllers;

import com.neueda.leap.dtos.OrderResponse;
import com.neueda.leap.dtos.OrderHistoryResponse;
import com.neueda.leap.dtos.PlaceOrderRequest;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.exceptions.AccountNotActiveException;
import com.neueda.leap.exceptions.AccountNotFoundException;
import com.neueda.leap.kafka.OrderEventPublisher;
import com.neueda.leap.kafka.events.OrderEvent;
import com.neueda.leap.model.Order;
import com.neueda.leap.model.OrderHistory;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.repositories.OrderHistoryRepository;
import com.neueda.leap.model.Account;
import com.neueda.leap.security.AccountAccess;
import com.neueda.leap.services.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Admins (ROLE_ADMIN) can view, place and cancel any order. Customers can only
 * act on orders belonging to accounts they own, and can only place orders
 * against an owned account that is ACTIVE.
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {
    private final OrderRepository orderRepository;
    private final AccountRepository accountRepository;
    private final OrderHistoryRepository orderHistoryRepository;
    private final OrderService orderService;
    private final OrderEventPublisher orderEventPublisher;
    private final AccountAccess accountAccess;

    /**
     * Places an order asynchronously by publishing to Kafka.
     * 
     * Returns 202 ACCEPTED immediately; processing happens asynchronously by the
     * OrderMessageListener. Clients should poll GET /api/v1/orders/{orderId} to
     * track order status.
     * 
     * Failures (invalid account, insufficient funds, etc.) are captured in the DLQ
     * for administrative review and replay.
     * 
     * @param request the order placement request with validation
     * @return 202 ACCEPTED with order ID for client tracking
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or @accountAccess.ownsAccount(authentication, #request.accountId())")
    public ResponseEntity<Map<String, Object>> placeOrder(@Valid @RequestBody PlaceOrderRequest request,
            Authentication authentication) {
        // Customers get an immediate 409 if their account has not been approved (or was
        // suspended/closed), instead of the order silently failing into the DLQ later
        if (!accountAccess.isAdmin(authentication)) {
            Account account = accountRepository.findById(request.accountId())
                    .orElseThrow(() -> new AccountNotFoundException("Account not found: " + request.accountId()));
            if (!account.isActive()) {
                throw new AccountNotActiveException("Account not active: " + request.accountId());
            }
        }


        // Generate order ID for this request
        UUID orderId = UUID.randomUUID();

        // Create order event from request
        OrderEvent event = new OrderEvent(
                orderId,
                request.accountId(),
                request.symbol(),
                request.side(),
                request.quantity(),
                request.price(),
                Instant.now());

        // Publish to Kafka for async processing
        // Message will be retried with exponential backoff and routed to DLQ on failure
        orderEventPublisher.publishEvent(event, request.accountId());

        log.info("Order published for async processing: orderId={}, accountId={}, symbol={}", 
                orderId, request.accountId(), request.symbol());

        // Return 202 ACCEPTED with order ID for client tracking
        Map<String, Object> response = new HashMap<>();
        response.put("orderId", orderId.toString());
        response.put("status", "ACCEPTED");
        response.put("message", "Order accepted for processing. Poll GET /api/v1/orders/{orderId} to track status.");

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getAllOrders(Authentication authentication) {
        // Admins see all orders (system monitoring); customers only see orders on
        // their own accounts
        List<Order> orders = accountAccess.isAdmin(authentication)
                ? orderRepository.findAll()
                : orderRepository.findByAccountIdIn(accountAccess.ownedAccounts(authentication).stream()
                        .map(Account::getId)
                        .toList());
        List<OrderResponse> responses = orders.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasRole('ADMIN') or @accountAccess.ownsOrder(authentication, #orderId)")
    @SuppressWarnings("null")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID orderId) {
        // Retrieves order by UUID; throws exception if not found to maintain REST
        // consistency
        Order order = orderRepository.findById(orderId)
                .orElseThrow(
                        () -> new com.neueda.leap.exceptions.OrderNotFoundException("Order not found: " + orderId));
        return ResponseEntity.ok(mapToResponse(order));
    }

    @DeleteMapping("/{orderId}")
    @PreAuthorize("hasRole('ADMIN') or @accountAccess.ownsOrder(authentication, #orderId)")
    public ResponseEntity<Void> cancelOrder(@PathVariable UUID orderId) {
        // Validates order exists before cancellation; prevents silently ignoring
        // requests for non-existent orders
        orderRepository.findById(orderId)
                .orElseThrow(
                        () -> new com.neueda.leap.exceptions.OrderNotFoundException("Order not found: " + orderId));

        // Delegates cancellation to service layer; service validates business rules
        // (status, timing)
        orderService.cancelOrder(orderId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/history/{orderId}")
    @PreAuthorize("hasRole('ADMIN') or @accountAccess.ownsOrderHistory(authentication, #orderId)")
    public ResponseEntity<OrderHistoryResponse> getOrderHistory(@PathVariable UUID orderId) {
        // Retrieves order history record for a cancelled/deleted order
        OrderHistory history = orderHistoryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new com.neueda.leap.exceptions.OrderNotFoundException(
                        "Order history not found: " + orderId));
        return ResponseEntity.ok(mapHistoryToResponse(history));
    }

    @GetMapping("/{accountId}/history")
    @PreAuthorize("hasRole('ADMIN') or @accountAccess.ownsAccount(authentication, #accountId)")
    public ResponseEntity<List<OrderHistoryResponse>> getAccountOrderHistory(@PathVariable Long accountId) {
        // Retrieves all order history records for an account; enables audit trail
        // queries even for deleted accounts
        List<OrderHistory> histories = orderHistoryRepository.findByAccountId(accountId);
        List<OrderHistoryResponse> responses = histories.stream()
                .map(this::mapHistoryToResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
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
                order.getCreatedOn());
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
