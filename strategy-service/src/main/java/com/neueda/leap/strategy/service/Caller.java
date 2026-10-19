package com.neueda.leap.strategy.service;

import java.util.Optional;

/**
 * Who is calling: a user's username, whether they are an admin, and their
 * trading account (admins have none).
 */
public record Caller(String username, boolean admin, Optional<Long> accountId) {
}
