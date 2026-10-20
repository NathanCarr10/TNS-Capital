package com.neueda.leap.strategy.service;

import java.util.Optional;

/**
 * Who is calling: a user's email, whether they are support staff (who view
 * every trader's account, docs/DECISIONS.md "Roles"), and their trading
 * account (support and admin users have none).
 */
public record Caller(String email, boolean support, Optional<Long> accountId) {
}
