package com.neueda.leap.dtos;

import com.neueda.leap.enums.AccountStatus;
import jakarta.validation.constraints.NotNull;

/**
 * DTO for an admin approving (ACTIVE), suspending or closing an account.
 */
public record UpdateAccountStatusRequest(
        @NotNull(message = "Status cannot be null")
        AccountStatus status) {
}
