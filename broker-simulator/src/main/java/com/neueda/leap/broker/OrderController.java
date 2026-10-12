package com.neueda.leap.broker;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * The broker's API. Orders are accepted straight away (202) and filled
 * later; the result arrives on the executions topic, not in this response.
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final Broker broker;

    public OrderController(Broker broker) {
        this.broker = broker;
    }

    public record Accepted(UUID orderId, String status) {
    }

    @PostMapping
    public ResponseEntity<Accepted> submit(@Valid @RequestBody BrokerOrder order) {
        broker.accept(order);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new Accepted(order.orderId(), "ACCEPTED"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .sorted()
                .reduce((a, b) -> a + "; " + b)
                .orElse("Invalid order");
        return ResponseEntity.badRequest().body(Map.of("errorCode", "VAL-422", "message", message));
    }
}
