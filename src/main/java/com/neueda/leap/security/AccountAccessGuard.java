package com.neueda.leap.security;

import com.neueda.leap.repositories.AccountRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

/**
 * Decides which accounts the caller may use.
 *
 * - ADMIN (token roles ["ADMIN"]) may access every account
 * - Anyone else may only access the account linked to their username
 *   (the token's "sub") in users.account_id, opened when they registered
 * - The strategy service (token roles ["STRATEGY"], from the auth service's
 *   client credentials) may place and read orders, checkTradingAccess, only
 *   on an account whose owner has an ACTIVE strategy. SecurityConfig keeps its
 *   token away from every other endpoint (docs/DECISIONS.md #16)
 *
 * Denials throw AccessDeniedException, returned as 403 AUTH-403.
 */
@Component
public class AccountAccessGuard {
    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";
    private static final String STRATEGY_AUTHORITY = "ROLE_STRATEGY";

    private final AccountRepository accountRepository;

    public AccountAccessGuard(AccountRepository accountRepository) {
        this.accountRepository = Objects.requireNonNull(accountRepository);
    }

    public boolean isAdmin() {
        return hasAuthority(ADMIN_AUTHORITY);
    }

    /**
     * @return whether the caller is the strategy service rather than a user
     */
    public boolean isStrategyService() {
        return hasAuthority(STRATEGY_AUTHORITY);
    }

    private static boolean hasAuthority(String name) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(authority -> name.equals(authority.getAuthority()));
    }

    /**
     * @return the caller's own account ID; empty if they have none (e.g. admins)
     */
    public Optional<Long> currentAccountId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return Optional.empty();
        }
        return accountRepository.findAccountIdByUsername(auth.getName());
    }

    /**
     * @throws AccessDeniedException unless the caller is an admin or owns the account
     */
    public void checkAccess(Long accountId) {
        if (isAdmin()) {
            return;
        }
        boolean owner = accountId != null && currentAccountId().map(accountId::equals).orElse(false);
        if (!owner) {
            throw new AccessDeniedException("Account " + accountId + " does not belong to the caller");
        }
    }

    /**
     * For placing and reading orders: what checkAccess allows, and the
     * strategy service on an account with an ACTIVE strategy.
     *
     * @throws AccessDeniedException otherwise
     */
    public void checkTradingAccess(Long accountId) {
        if (!isStrategyService()) {
            checkAccess(accountId);
            return;
        }
        if (accountId == null || !accountRepository.hasActiveStrategy(accountId)) {
            throw new AccessDeniedException("Account " + accountId + " has no active strategy");
        }
    }
}
