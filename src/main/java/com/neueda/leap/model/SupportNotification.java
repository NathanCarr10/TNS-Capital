package com.neueda.leap.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "support_notifications")
public class SupportNotification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_role", nullable = false, length = 20)
    private String recipientRole;

    @Column(name = "recipient_username", length = 255)
    private String recipientUsername;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected SupportNotification() {
    }

    public SupportNotification(String recipientRole, String recipientUsername, UUID conversationId, Long messageId,
            Instant createdAt) {
        this.recipientRole = recipientRole;
        this.recipientUsername = recipientUsername;
        this.conversationId = conversationId;
        this.messageId = messageId;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getRecipientRole() { return recipientRole; }
    public String getRecipientUsername() { return recipientUsername; }
    public UUID getConversationId() { return conversationId; }
    public Long getMessageId() { return messageId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReadAt() { return readAt; }
}