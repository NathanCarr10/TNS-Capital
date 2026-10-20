package com.neueda.leap.dtos;

import com.neueda.leap.model.SupportMessage.SenderRole;

import java.time.Instant;

public record SupportMessageResponse(Long id, String senderUsername, SenderRole senderRole, String body,
        Instant createdAt) {
}