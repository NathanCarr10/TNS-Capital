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
 * - SUPPORT (token roles ["SUPPORT"]) may access every account: support staff
 *   look after traders' accounts in the trading app
 * - Anyone else, ADMIN included, may only access the account linked to their
 *   username (the token's "sub") in users.account_id, opened when they
 *   registered. Admins run the analytics dashboard and operations, not
 *   traders' accounts (docs/DECISIONS.md, "Roles")
 *
 * Denials throw AccessDeniedException, returned as 403 AUTH-403.
 */
@Component
public class AccountAccessGuard {
    private static final String SUPPORT_AUTHORITY = "ROLE_SUPPORT";

    private final AccountRepository accountRepository;

    public AccountAccessGuard(AccountRepository accountRepository) {
        this.accountRepository = Objects.requireNonNull(accountRepository);
    }

    public boolean isSupport() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(authority -> SUPPORT_AUTHORITY.equals(authority.getAuthority()));
    }

    /**
     * @return the caller's own account ID; empty if they have none (support and admin users)
     */
    public Optional<Long> currentAccountId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return Optional.empty();
        }
        return accountRepository.findAccountIdByUsername(auth.getName());
    }

    /**
     * @throws AccessDeniedException unless the caller is support or owns the account
     */
    public void checkAccess(Long accountId) {
        if (isSupport()) {
            return;
        }
        boolean owner = accountId != null && currentAccountId().map(accountId::equals).orElse(false);
        if (!owner) {
            throw new AccessDeniedException("Account " + accountId + " does not belong to the caller");
        }
    }
}
