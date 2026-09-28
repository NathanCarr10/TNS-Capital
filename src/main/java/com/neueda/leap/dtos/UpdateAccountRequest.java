package com.neueda.leap.dtos;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO for partial account updates (PATCH requests).
 * All fields are optional - only provided fields will be updated.
 */
public record UpdateAccountRequest(
        @NotBlank(message = "Holder name cannot be blank")
        String holderName) {
}
