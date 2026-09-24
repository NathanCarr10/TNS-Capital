package com.neueda.leap.controllers;

import com.neueda.leap.dtos.OrderResponse;
import com.neueda.leap.dtos.PlaceOrderRequest;
import com.neueda.leap.exceptions.AccountNotFoundException;
import com.neueda.leap.model.Order;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.services.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final OrderRepository orderRepository;
    private final AccountRepository accountRepository;
    private final OrderService orderService;
    
    public OrderController(OrderRepository orderRepository,
                        AccountRepository accountRepository,
                        OrderService orderService) {

        this.orderRepository = orderRepository;
        this.accountRepository = accountRepository;
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
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new com.neueda.leap.exceptions.OrderNotFoundException("Order not found: " + orderId));
        
        // Delegates cancellation to service layer; service validates business rules (status, timing)
        orderService.cancelOrder(orderId);
        return ResponseEntity.noContent().build();
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
}
