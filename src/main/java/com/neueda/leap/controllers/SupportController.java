package com.neueda.leap.controllers;

import com.neueda.leap.dtos.SupportConversationRequest;
import com.neueda.leap.dtos.SupportConversationResponse;
import com.neueda.leap.dtos.SupportMessageRequest;
import com.neueda.leap.dtos.SupportMessageResponse;
import com.neueda.leap.dtos.SupportNotificationResponse;
import com.neueda.leap.model.SupportConversation;
import com.neueda.leap.model.SupportMessage;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.services.SupportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/support/conversations")
public class SupportController {
    private final SupportService service;
    private final AccountRepository accounts;

    public SupportController(SupportService service, AccountRepository accounts) {
        this.service = service;
        this.accounts = accounts;
    }

    @GetMapping
    public List<SupportConversationResponse> list() {
        return service.listConversations().stream().map(this::response).toList();
    }

    @PostMapping
    public ResponseEntity<SupportConversationResponse> create(@Valid @RequestBody SupportConversationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(response(service.createConversation(request)));
    }

    @PostMapping("/{conversationId}/messages")
    public SupportConversationResponse message(@PathVariable UUID conversationId,
            @Valid @RequestBody SupportMessageRequest request) {
        return response(service.addMessage(conversationId, request));
    }

    @PostMapping("/{conversationId}/close")
    public SupportConversationResponse close(@PathVariable UUID conversationId) {
        return response(service.closeConversation(conversationId));
    }

    @GetMapping("/notifications/unread")
    public SupportNotificationResponse unreadNotifications() {
        return new SupportNotificationResponse(service.unreadSupportNotifications());
    }

    @GetMapping(value = "/{conversationId}/events", produces = "text/event-stream")
    public SseEmitter events(@PathVariable UUID conversationId) {
        return service.subscribe(conversationId);
    }

    private SupportConversationResponse response(SupportConversation conversation) {
        return new SupportConversationResponse(conversation.getId(), conversation.getCustomerUsername(),
            accounts.findAccountIdByUsername(conversation.getCustomerUsername()).orElse(null),
                conversation.getSubject(), conversation.getStatus(), conversation.getCreatedAt(), conversation.getUpdatedAt(),
                service.messages(conversation.getId()).stream().map(this::messageResponse).toList());
    }

    private SupportMessageResponse messageResponse(SupportMessage message) {
        return new SupportMessageResponse(message.getId(), message.getSenderUsername(), message.getSenderRole(),
                message.getBody(), message.getCreatedAt());
    }
}