package com.neueda.leap.services;

import com.neueda.leap.dtos.SupportConversationRequest;
import com.neueda.leap.dtos.SupportMessageRequest;
import com.neueda.leap.exceptions.SupportConversationClosedException;
import com.neueda.leap.model.SupportConversation;
import com.neueda.leap.model.SupportMessage;
import com.neueda.leap.model.SupportNotification;
import com.neueda.leap.repositories.SupportConversationRepository;
import com.neueda.leap.repositories.SupportMessageRepository;
import com.neueda.leap.repositories.SupportNotificationRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SupportService {
    private static final String CONVERSATION_NOT_FOUND = "Support conversation not found";
    // A stream is authorised once, when it opens, so it ends with the access token's lifetime
    // (15 minutes) and the client reconnects with its current token (OWASP review A01-13)
    static final Duration STREAM_TIMEOUT = Duration.ofMinutes(15);
    private final SupportConversationRepository conversations;
    private final SupportMessageRepository messages;
    private final SupportNotificationRepository notifications;
    private final Map<UUID, CopyOnWriteArrayList<SseEmitter>> streams = new ConcurrentHashMap<>();

        public SupportService(SupportConversationRepository conversations, SupportMessageRepository messages,
            SupportNotificationRepository notifications) {
        this.conversations = conversations;
        this.messages = messages;
        this.notifications = notifications;
    }

    public List<SupportConversation> listConversations() {
        return isSupport() ? conversations.findAllByOrderByUpdatedAtDesc()
                : conversations.findByCustomerEmailOrderByUpdatedAtDesc(email());
    }

    @Transactional
    public SupportConversation createConversation(SupportConversationRequest request) {
        Instant now = Instant.now();
        SupportConversation conversation = conversations.save(new SupportConversation(email(), request.subject().trim(), now));
        addMessage(conversation, request.body(), SupportMessage.SenderRole.CUSTOMER, now);
        return conversation;
    }

    @Transactional
    public SupportConversation addMessage(UUID conversationId, SupportMessageRequest request) {
        SupportConversation conversation = conversations.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException(CONVERSATION_NOT_FOUND));
        checkAccess(conversation);
        if (conversation.getStatus() == SupportConversation.Status.CLOSED) {
            throw new SupportConversationClosedException("Support conversation " + conversationId + " is closed");
        }
        Instant now = Instant.now();
        SupportMessage.SenderRole role = isSupport() ? SupportMessage.SenderRole.SUPPORT : SupportMessage.SenderRole.CUSTOMER;
        addMessage(conversation, request.body(), role, now);
        return conversation;
    }

    @Transactional
    public SupportConversation closeConversation(UUID conversationId) {
        if (!isSupport()) throw new AccessDeniedException("Support role required");
        SupportConversation conversation = conversations.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException(CONVERSATION_NOT_FOUND));
        conversation.close();
        return conversations.save(conversation);
    }

    private void addMessage(SupportConversation conversation, String body, SupportMessage.SenderRole role, Instant now) {
        SupportMessage message = messages.save(new SupportMessage(conversation.getId(), email(), role, body.trim(), now));
        conversation.touch(now);
        conversations.save(conversation);
        if (role == SupportMessage.SenderRole.CUSTOMER) {
            notifications.save(new SupportNotification("SUPPORT", null, conversation.getId(), message.getId(), now));
        } else {
            notifications.save(new SupportNotification("CUSTOMER", conversation.getCustomerEmail(), conversation.getId(), message.getId(), now));
        }
        publish(conversation.getId(), message);
    }

    public List<SupportMessage> messages(UUID conversationId) {
        SupportConversation conversation = conversations.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException(CONVERSATION_NOT_FOUND));
        checkAccess(conversation);
        return messages.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    public long unreadSupportNotifications() {
        if (!isSupport()) throw new AccessDeniedException("Support role required");
        return notifications.countByRecipientRoleAndReadAtIsNull("SUPPORT");
    }

    @Transactional
    public void markSupportNotificationsRead() {
        if (!isSupport()) throw new AccessDeniedException("Support role required");
        notifications.markSupportNotificationsRead();
    }

    public SseEmitter subscribe(UUID conversationId) {
        SupportConversation conversation = conversations.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException(CONVERSATION_NOT_FOUND));
        checkAccess(conversation);
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT.toMillis());
        streams.computeIfAbsent(conversationId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        Runnable remove = () -> removeStream(conversationId, emitter);
        emitter.onCompletion(remove);
        // Ends the stream normally, so the client sees it close and reconnects
        emitter.onTimeout(emitter::complete);
        emitter.onError(ignored -> remove.run());
        try {
            emitter.send(SseEmitter.event().name("connected").data("connected"));
        } catch (IOException exception) {
            remove.run();
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    private void publish(UUID conversationId, SupportMessage message) {
        List<SseEmitter> subscribers = streams.getOrDefault(conversationId, new CopyOnWriteArrayList<>());
        for (SseEmitter emitter : subscribers) {
            try {
                emitter.send(SseEmitter.event().name("message").data(message));
            } catch (IOException exception) {
                removeStream(conversationId, emitter);
            }
        }
    }

    private void removeStream(UUID conversationId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> subscribers = streams.get(conversationId);
        if (subscribers != null) {
            subscribers.remove(emitter);
            if (subscribers.isEmpty()) streams.remove(conversationId, subscribers);
        }
    }

    private void checkAccess(SupportConversation conversation) {
        if (!isSupport() && !conversation.getCustomerEmail().equals(email())) {
            throw new AccessDeniedException("Support conversation does not belong to the caller");
        }
    }

    private boolean isSupport() {
        return authentication().getAuthorities().stream().anyMatch(a -> "ROLE_SUPPORT".equals(a.getAuthority()));
    }

    private String email() { return authentication().getName(); }

    private Authentication authentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) throw new AccessDeniedException("Authentication required");
        return authentication;
    }
}