package com.neueda.leap.dtos;

/**
 * Allow-list for account holder names: letters in any language, digits,
 * spaces and . , ' - so characters used by script payloads (< > " / = ( ))
 * are rejected. Keep in step with the auth service's RegisterDto and
 * frontend/src/app/core/validation/input-patterns.ts.
 */
public final class HolderName {

    public static final String PATTERN = "^[\\p{L}\\p{M}\\p{N} .,'-]+$";
    public static final String MESSAGE = "Holder name can only contain letters, numbers, spaces and . , ' -";

    private HolderName() {
    }
}
