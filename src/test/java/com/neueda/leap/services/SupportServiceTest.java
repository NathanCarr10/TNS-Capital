package com.neueda.leap.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.neueda.leap.dtos.SupportMessageRequest;
import com.neueda.leap.model.SupportConversation;
import com.neueda.leap.model.SupportMessage;
import com.neueda.leap.model.SupportNotification;
import com.neueda.leap.repositories.SupportConversationRepository;
import com.neueda.leap.repositories.SupportMessageRepository;
import com.neueda.leap.repositories.SupportNotificationRepository;

@DisplayName("SupportService")
class SupportServiceTest {
    private static final Instant CREATED_AT = Instant.parse("2026-10-19T12:00:00Z");

    private final SupportConversationRepository conversations = org.mockito.Mockito.mock(SupportConversationRepository.class);
    private final SupportMessageRepository messages = org.mockito.Mockito.mock(SupportMessageRepository.class);
    private final SupportNotificationRepository notifications = org.mockito.Mockito.mock(SupportNotificationRepository.class);
    private final SupportService service = new SupportService(conversations, messages, notifications);
    private SupportConversation conversation;
    private UUID conversationId;

    @BeforeEach
    void setUp() {
        conversation = new SupportConversation("trader1", "Order question", CREATED_AT);
        conversationId = conversation.getId();
        when(conversations.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        SecurityContextHolder.getContext().setAuthentication(
            new TestingAuthenticationToken("support", "password", List.of(new SimpleGrantedAuthority("ROLE_SUPPORT"))));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void supportCanCloseConversation() {
        SupportConversation closed = service.closeConversation(conversationId);

        assertEquals(SupportConversation.Status.CLOSED, closed.getStatus());
        verify(conversations).save(conversation);
    }

    @Test
    void customerCannotCloseConversation() {
        SecurityContextHolder.getContext().setAuthentication(
            new TestingAuthenticationToken("trader1", "password", List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        assertThrows(AccessDeniedException.class, () -> service.closeConversation(conversationId));
    }

    @Test
    void messageCreatesSupportNotification() {
        SupportMessage saved = new SupportMessage(conversationId, "support", SupportMessage.SenderRole.SUPPORT,
                "We are looking into this", CREATED_AT);
        when(messages.save(any())).thenReturn(saved);

        service.addMessage(conversationId, new SupportMessageRequest("We are looking into this"));

        ArgumentCaptor<SupportNotification> notification = ArgumentCaptor.forClass(SupportNotification.class);
        verify(notifications).save(notification.capture());
        assertEquals("CUSTOMER", notification.getValue().getRecipientRole());
        assertEquals("trader1", notification.getValue().getRecipientUsername());
    }
}