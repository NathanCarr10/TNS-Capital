package com.neueda.leap.strategy.service;

import com.neueda.leap.strategy.accounts.PlatformLookup;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

/**
 * Builds the Caller from the verified token: the account is looked up by
 * username in users.account_id, as the trading API does.
 */
@Component
public class CallerResolver {
    private final PlatformLookup platform;

    public CallerResolver(PlatformLookup platform) {
        this.platform = Objects.requireNonNull(platform);
    }

    public Caller resolve(Authentication authentication) {
        boolean support = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_SUPPORT".equals(authority.getAuthority()));
        Optional<Long> accountId = support ? Optional.empty() : platform.accountIdOf(authentication.getName());
        return new Caller(authentication.getName(), support, accountId);
    }
}
