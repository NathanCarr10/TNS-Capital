package com.neueda.leap.model;

import java.time.Instant;
import java.util.UUID;
import com.neueda.leap.enums.DLQStatus;
import jakarta.persistence.*;

/**
 * Dead-Letter Message entity for storing failed order messages.
 * 
 * Captures failed order events from Kafka with root cause information,
 * enabling administrative review and manual replay.
 */
@Entity
@Table(name = "dlq_messages", indexes = {
        @Index(name = "idx_dlq_status", columnList = "status"),
        @Index(name = "idx_dlq_created_on", columnList = "created_on"),
        @Index(name = "idx_dlq_order_id", columnList = "original_order_id"),
        @Index(name = "idx_dlq_failure_type", columnList = "failure_type")
})
public class DeadLetterMessage {

    @Id
    private UUID id;

    @Column(name = "original_order_id")
    private UUID originalOrderId;

    @Column(name = "original_message", columnDefinition = "TEXT", nullable = false)
    private String originalMessage;

    @Column(name = "failure_reason", columnDefinition = "TEXT", nullable = false)
    private String failureReason;

    @Column(name = "failure_type", length = 100, nullable = false)
    private String failureType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private DLQStatus status;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "created_on", nullable = false)
    private Instant createdOn;

    @Column(name = "last_retry_on")
    private Instant lastRetryOn;

    @Column(name = "resolved_on")
    private Instant resolvedOn;

    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    @Column(name = "is_retryable", nullable = false)
    private Boolean isRetryable = true;

    protected DeadLetterMessage() {
        // JPA no-arg constructor
    }

    public DeadLetterMessage(UUID id, UUID originalOrderId, String originalMessage,
            String failureReason, String failureType, Instant createdOn) {
        if (id == null) {
            throw new IllegalArgumentException("ID cannot be null");
        }
        if (originalMessage == null || originalMessage.trim().isEmpty()) {
            throw new IllegalArgumentException("Original message cannot be null or empty");
        }
        if (failureReason == null || failureReason.trim().isEmpty()) {
            throw new IllegalArgumentException("Failure reason cannot be null or empty");
        }
        if (failureType == null || failureType.trim().isEmpty()) {
            throw new IllegalArgumentException("Failure type cannot be null or empty");
        }
        if (createdOn == null) {
            throw new IllegalArgumentException("Created timestamp cannot be null");
        }

        this.id = id;
        this.originalOrderId = originalOrderId;
        this.originalMessage = originalMessage;
        this.failureReason = failureReason;
        this.failureType = failureType;
        this.status = DLQStatus.PENDING;
        this.retryCount = 0;
        this.createdOn = createdOn;
    }

    // Getters
    public UUID getId() {
        return id;
    }

    public UUID getOriginalOrderId() {
        return originalOrderId;
    }

    public String getOriginalMessage() {
        return originalMessage;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getFailureType() {
        return failureType;
    }

    public DLQStatus getStatus() {
        return status;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public Instant getCreatedOn() {
        return createdOn;
    }

    public Instant getLastRetryOn() {
        return lastRetryOn;
    }

    public Instant getResolvedOn() {
        return resolvedOn;
    }

    public String getAdminNotes() {
        return adminNotes;
    }

    public Boolean getIsRetryable() {
        return isRetryable;
    }

    // Setters
    public void setStatus(DLQStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Status cannot be null");
        }
        this.status = status;
    }

    public void setRetryCount(Integer retryCount) {
        if (retryCount == null || retryCount < 0) {
            throw new IllegalArgumentException("Retry count must be non-negative");
        }
        this.retryCount = retryCount;
    }

    public void setLastRetryOn(Instant lastRetryOn) {
        this.lastRetryOn = lastRetryOn;
    }

    public void setResolvedOn(Instant resolvedOn) {
        this.resolvedOn = resolvedOn;
    }

    public void setAdminNotes(String adminNotes) {
        this.adminNotes = adminNotes;
    }

    public void setIsRetryable(Boolean isRetryable) {
        if (isRetryable == null) {
            throw new IllegalArgumentException("isRetryable cannot be null");
        }
        this.isRetryable = isRetryable;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        DeadLetterMessage other = (DeadLetterMessage) obj;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "DeadLetterMessage{" +
                "id=" + id +
                ", originalOrderId=" + originalOrderId +
                ", failureType='" + failureType + '\'' +
                ", status=" + status +
                ", retryCount=" + retryCount +
                ", isRetryable=" + isRetryable +
                ", createdOn=" + createdOn +
                '}';
    }
}
