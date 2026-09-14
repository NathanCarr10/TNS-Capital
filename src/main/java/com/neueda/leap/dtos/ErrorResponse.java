package com.neueda.leap.dtos;

// public class ErrorResponse {

// }

import java.time.LocalDateTime;

public record ErrorResponse(
                String errorCode,
                String message,
                LocalDateTime timestamp) {
}