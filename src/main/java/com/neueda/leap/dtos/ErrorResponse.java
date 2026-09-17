package com.neueda.leap.dtos;

import java.time.LocalDateTime;

public record ErrorResponse(
        String errorCode,
        String message,
        LocalDateTime timestamp) {
}