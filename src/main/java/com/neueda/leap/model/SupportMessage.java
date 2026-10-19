package com.neueda.leap.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "support_messages")
public class SupportMessage {
    public enum SenderRole { CUSTOMER, SUPPORT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "sender_username", nullable = false, length = 255)
    private String senderUsername;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_role", nullable = false, length = 20)
    private SenderRole senderRole;

    @Column(nullable = false, length = 4000)
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SupportMessage() {
    }

    public SupportMessage(UUID conversationId, String senderUsername, SenderRole senderRole, String body,
            Instant createdAt) {
        this.conversationId = conversationId;
        this.senderUsername = senderUsername;
        this.senderRole = senderRole;
        this.body = body;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public UUID getConversationId() { return conversationId; }
    public String getSenderUsername() { return senderUsername; }
    public SenderRole getSenderRole() { return senderRole; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
}