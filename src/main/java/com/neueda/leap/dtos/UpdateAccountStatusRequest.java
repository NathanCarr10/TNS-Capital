package com.neueda.leap.dtos;

import com.neueda.leap.enums.AccountStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Body for suspending or reactivating an account (ACTIVE or SUSPENDED).
 */
public record UpdateAccountStatusRequest(
        @NotNull(message = "Status is required")
        AccountStatus status) {
}
