package com.neueda.leap.controllers;

import com.neueda.leap.dtos.OrderResponse;
import com.neueda.leap.dtos.OrderHistoryResponse;
import com.neueda.leap.dtos.PlaceOrderRequest;
import com.neueda.leap.exceptions.AccountNotFoundException;
import com.neueda.leap.model.Order;
import com.neueda.leap.model.OrderHistory;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.repositories.OrderHistoryRepository;
import com.neueda.leap.services.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final OrderRepository orderRepository;
    private final AccountRepository accountRepository;
    private final OrderHistoryRepository orderHistoryRepository;
    private final OrderService orderService;
    
    public OrderController(OrderRepository orderRepository,
                        AccountRepository accountRepository,
                        OrderHistoryRepository orderHistoryRepository,
                        OrderService orderService) {

        this.orderRepository = orderRepository;
        this.accountRepository = accountRepository;
        this.orderHistoryRepository = orderHistoryRepository;
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
        // Validates account exists before delegating to service; prevents orphaned orders against non-existent accounts
        accountRepository.findById(request.accountId())
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + request.accountId()));
        
        // Delegates order processing to service layer; controller owns HTTP routing, service owns business logic
        Order order = orderService.placeOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapToResponse(order));
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getAllOrders() {
        // Queries all orders; enables bulk retrieval and monitoring of system order flow
        List<Order> orders = orderRepository.findAll();
        List<OrderResponse> responses = orders.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID orderId) {
        // Retrieves order by UUID; throws exception if not found to maintain REST consistency
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new com.neueda.leap.exceptions.OrderNotFoundException("Order not found: " + orderId));
        return ResponseEntity.ok(mapToResponse(order));
    }

    @DeleteMapping("/{orderId}")
    public ResponseEntity<Void> cancelOrder(@PathVariable UUID orderId) {
        // Validates order exists before cancellation; prevents silently ignoring requests for non-existent orders
        orderRepository.findById(orderId)
                .orElseThrow(() -> new com.neueda.leap.exceptions.OrderNotFoundException("Order not found: " + orderId));
        
        // Delegates cancellation to service layer; service validates business rules (status, timing)
        orderService.cancelOrder(orderId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/history/{orderId}")
    public ResponseEntity<OrderHistoryResponse> getOrderHistory(@PathVariable UUID orderId) {
        // Retrieves order history record for a cancelled/deleted order
        OrderHistory history = orderHistoryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new com.neueda.leap.exceptions.OrderNotFoundException("Order history not found: " + orderId));
        return ResponseEntity.ok(mapHistoryToResponse(history));
    }

    @GetMapping("/{accountId}/history")
    public ResponseEntity<List<OrderHistoryResponse>> getAccountOrderHistory(@PathVariable Long accountId) {
        // Retrieves all order history records for an account; enables audit trail queries even for deleted accounts
        List<OrderHistory> histories = orderHistoryRepository.findByAccountId(accountId);
        List<OrderHistoryResponse> responses = histories.stream()
                .map(this::mapHistoryToResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    private OrderResponse mapToResponse(Order order) {
        // Converts Order entity to response; UUID to UUID, Instant to Instant for proper REST contract
        return new OrderResponse(
                order.getId(),
                order.getAccountId(),
                order.getSymbol(),
                order.getSide(),
                order.getQuantity(),
                order.getPrice(),
                order.getStatus(),
                order.getCreatedOn()
        );
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
                history.getDeletedOn()
        );
    }
}
