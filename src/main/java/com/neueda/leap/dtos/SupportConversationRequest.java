package com.neueda.leap.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupportConversationRequest(
        @NotBlank @Size(max = 120) String subject,
        @NotBlank @Size(max = 4000) String body) {
}