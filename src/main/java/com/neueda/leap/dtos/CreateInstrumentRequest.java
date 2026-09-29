package com.neueda.leap.dtos;

import jakarta.validation.constraints.*;

/**
 * DTO for creating a new instrument with comprehensive input validation.
 * Enforces data constraints at the API boundary to prevent invalid instrument creation.
 */
public record CreateInstrumentRequest(
        @NotNull(message = "Symbol is required")
        @NotBlank(message = "Symbol cannot be blank")
        @Pattern(regexp = "^[A-Z]{1,10}$", message = "Symbol must contain 1-10 uppercase letters")
        String symbol,
        
        @NotNull(message = "Name is required")
        @NotBlank(message = "Name cannot be blank")
        @Size(min = 1, max = 255, message = "Name must be between 1 and 255 characters")
        String name,
        
        @NotNull(message = "Asset class is required")
        @NotBlank(message = "Asset class cannot be blank")
        @Pattern(regexp = "^[A-Z_]{1,20}$", message = "Asset class must contain uppercase letters and underscores only")
        String assetClass,
        
        @NotNull(message = "Currency is required")
        @NotBlank(message = "Currency cannot be blank")
        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code (e.g., USD)")
        String currency,
        
        @NotNull(message = "Tradable flag is required")
        boolean tradable) {
}
