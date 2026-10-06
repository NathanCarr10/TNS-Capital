package com.neueda.leap.security;

import com.neueda.leap.model.Account;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.OrderHistoryRepository;
import com.neueda.leap.repositories.OrderRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Ownership checks used by @PreAuthorize expressions, e.g.
 * {@code @PreAuthorize("hasRole('ADMIN') or @accountAccess.ownsAccount(authentication, #accountId)")}.
 *
 * A caller "owns" an account when the JWT subject (authentication.getName())
 * matches the account's ownerUsername. Every method returns false rather than
 * throwing when the account/order does not exist, so a customer probing for
 * other IDs gets 403 and learns nothing about which IDs exist.
 */
@Component("accountAccess")
public class AccountAccess {
    public static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    private final AccountRepository accountRepository;
    private final OrderRepository orderRepository;
    private final OrderHistoryRepository orderHistoryRepository;

    public AccountAccess(AccountRepository accountRepository,
            OrderRepository orderRepository,
            OrderHistoryRepository orderHistoryRepository) {
        this.accountRepository = accountRepository;
        this.orderRepository = orderRepository;
        this.orderHistoryRepository = orderHistoryRepository;
    }

    public boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> ADMIN_AUTHORITY.equals(authority.getAuthority()));
    }

    public boolean ownsAccount(Authentication authentication, Long accountId) {
        if (authentication == null || accountId == null) {
            return false;
        }
        return accountRepository.findById(accountId)
                .map(account -> isOwner(authentication, account))
                .orElse(false);
    }

    public boolean ownsOrder(Authentication authentication, UUID orderId) {
        if (orderId == null) {
            return false;
        }
        return orderRepository.findById(orderId)
                .map(order -> ownsAccount(authentication, order.getAccountId()))
                .orElse(false);
    }

    public boolean ownsOrderHistory(Authentication authentication, UUID orderId) {
        if (orderId == null) {
            return false;
        }
        return orderHistoryRepository.findByOrderId(orderId)
                .map(history -> ownsAccount(authentication, history.getAccountId()))
                .orElse(false);
    }

    /** Accounts belonging to the caller; empty when the customer has no account. */
    public List<Account> ownedAccounts(Authentication authentication) {
        return accountRepository.findByOwnerUsername(authentication.getName());
    }

    private boolean isOwner(Authentication authentication, Account account) {
        return account.getOwnerUsername() != null
                && account.getOwnerUsername().equals(authentication.getName());
    }
}
