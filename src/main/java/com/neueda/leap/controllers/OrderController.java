package com.neueda.leap.controllers;

import com.neueda.leap.dtos.OrderResponse;
import com.neueda.leap.dtos.OrderHistoryResponse;
import com.neueda.leap.dtos.PlaceOrderRequest;
import com.neueda.leap.model.Order;
import com.neueda.leap.model.OrderHistory;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.repositories.OrderHistoryRepository;
import com.neueda.leap.services.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {
    private final OrderRepository orderRepository;
    private final OrderHistoryRepository orderHistoryRepository;
    private final OrderService orderService;

    /**
     * Places an order asynchronously.
     *
     * The request is checked against the account, instrument and idempotency
     * rules first, so those failures return the spec's error codes (ACC-404,
     * ACC-403, INS-404, ORD-409) straight away. A valid order is published to
     * Kafka and 202 ACCEPTED is returned once the broker has it. Funds and
     * holdings are checked when the order executes; poll
     * GET /api/v1/orders/{orderId} for the outcome (FILLED or REJECTED with a
     * statusReason).
     *
     * @param request the order placement request with validation
     * @return 202 ACCEPTED with order ID for client tracking
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
        UUID orderId = orderService.submitOrder(request);

        log.info("Order published for async processing: orderId={}, accountId={}, symbol={}",
                orderId, request.accountId(), request.symbol());

        Map<String, Object> response = new HashMap<>();
        response.put("orderId", orderId.toString());
        response.put("status", "ACCEPTED");
        response.put("message", "Order accepted for processing. Poll GET /api/v1/orders/{orderId} to track status.");

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getAllOrders() {
        // Queries all orders; enables bulk retrieval and monitoring of system order
        // flow
        List<Order> orders = orderRepository.findAll();
        List<OrderResponse> responses = orders.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{orderId}")
    @SuppressWarnings("null")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID orderId) {
        // Retrieves order by UUID; throws exception if not found to maintain REST
        // consistency
        Order order = orderRepository.findById(orderId)
                .orElseThrow(
                        () -> new com.neueda.leap.exceptions.OrderNotFoundException("Order not found: " + orderId));
        return ResponseEntity.ok(mapToResponse(order));
    }

    /**
     * Cancels a working (NEW) order and returns it with its updated status.
     * Unknown orders return ORD-404; filled, rejected or already cancelled
     * orders return ORD-409.
     */
    @DeleteMapping("/{orderId}")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable UUID orderId) {
        Order cancelled = orderService.cancelOrder(orderId);
        return ResponseEntity.ok(mapToResponse(cancelled));
    }

    @GetMapping("/history/{orderId}")
    public ResponseEntity<OrderHistoryResponse> getOrderHistory(@PathVariable UUID orderId) {
        // Retrieves order history record for a cancelled/deleted order
        OrderHistory history = orderHistoryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new com.neueda.leap.exceptions.OrderNotFoundException(
                        "Order history not found: " + orderId));
        return ResponseEntity.ok(mapHistoryToResponse(history));
    }

    @GetMapping("/{accountId}/history")
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
                order.getStatusReason(),
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
