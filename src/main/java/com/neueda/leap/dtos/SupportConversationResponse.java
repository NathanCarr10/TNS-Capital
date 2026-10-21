package com.neueda.leap.dtos;

import com.neueda.leap.model.SupportConversation.Status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SupportConversationResponse(UUID id, String customerEmail, Long accountId, String subject, Status status,
        Instant createdAt, Instant updatedAt, List<SupportMessageResponse> messages) {
}