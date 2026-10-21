package com.neueda.leap.security;

import com.neueda.leap.repositories.AccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("AccountAccessGuard")
class AccountAccessGuardTest {
    private AccountRepository accountRepository;
    private AccountAccessGuard guard;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        guard = new AccountAccessGuard(accountRepository);
        when(accountRepository.findAccountIdByEmail("trader@example.com")).thenReturn(Optional.of(7L));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static void loginAs(String email, String... roles) {
        String[] authorities = java.util.Arrays.stream(roles).map(r -> "ROLE_" + r).toArray(String[]::new);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(email, null, authorities));
    }

    @Test
    @DisplayName("A user may access their own account")
    void ownerIsAllowed() {
        loginAs("trader@example.com", "USER");

        assertDoesNotThrow(() -> guard.checkAccess(7L));
        assertFalse(guard.isSupport());
        assertEquals(Optional.of(7L), guard.currentAccountId());
    }

    @Test
    @DisplayName("A user may not access another user's account")
    void otherAccountIsDenied() {
        loginAs("trader@example.com", "USER");

        assertThrows(AccessDeniedException.class, () -> guard.checkAccess(8L));
    }

    @Test
    @DisplayName("A user without a linked account is denied every account")
    void userWithoutAccountIsDenied() {
        loginAs("stranger@example.com", "USER");
        when(accountRepository.findAccountIdByEmail("stranger@example.com")).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () -> guard.checkAccess(7L));
    }

    @Test
    @DisplayName("A null account ID is denied")
    void nullAccountIsDenied() {
        loginAs("trader@example.com", "USER");

        assertThrows(AccessDeniedException.class, () -> guard.checkAccess(null));
    }

    @Test
    @DisplayName("Support may access every account without an ownership lookup")
    void supportIsAllowedEverywhere() {
        loginAs("support", "SUPPORT");

        assertTrue(guard.isSupport());
        assertDoesNotThrow(() -> guard.checkAccess(8L));
        verify(accountRepository, never()).findAccountIdByEmail(any());
    }

    @Test
    @DisplayName("An admin is not support: with no account of their own, every account is denied")
    void adminIsNotSupport() {
        loginAs("admin@tns-capital.local", "ADMIN");
        when(accountRepository.findAccountIdByEmail("admin@tns-capital.local")).thenReturn(Optional.empty());

        assertFalse(guard.isSupport());
        assertThrows(AccessDeniedException.class, () -> guard.checkAccess(8L));
    }

    @Test
    @DisplayName("Without authentication nothing is accessible")
    void unauthenticatedIsDenied() {
        assertFalse(guard.isSupport());
        assertEquals(Optional.empty(), guard.currentAccountId());
        assertThrows(AccessDeniedException.class, () -> guard.checkAccess(7L));
    }

    @Test
    @DisplayName("The strategy service may trade for an account with an ACTIVE strategy")
    void strategyServiceTradesForActiveStrategy() {
        loginAs("service:strategy-service", "STRATEGY");
        when(accountRepository.hasActiveStrategy(7L)).thenReturn(true);

        assertTrue(guard.isStrategyService());
        assertFalse(guard.isSupport());
        assertDoesNotThrow(() -> guard.checkTradingAccess(7L));
    }

    @Test
    @DisplayName("The strategy service may not trade for an account without an ACTIVE strategy")
    void strategyServiceRefusedWithoutActiveStrategy() {
        loginAs("service:strategy-service", "STRATEGY");
        when(accountRepository.hasActiveStrategy(8L)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> guard.checkTradingAccess(8L));
        assertThrows(AccessDeniedException.class, () -> guard.checkTradingAccess(null));
    }

    @Test
    @DisplayName("The strategy service gets no other access to an account, even with an ACTIVE strategy")
    void strategyServiceHasNoAccountAccess() {
        loginAs("service:strategy-service", "STRATEGY");
        when(accountRepository.hasActiveStrategy(7L)).thenReturn(true);

        assertThrows(AccessDeniedException.class, () -> guard.checkAccess(7L));
    }

    @Test
    @DisplayName("Users trade under the usual ownership rule, whatever strategies exist")
    void usersTradeOnlyTheirOwnAccount() {
        loginAs("trader@example.com", "USER");

        assertDoesNotThrow(() -> guard.checkTradingAccess(7L));
        assertThrows(AccessDeniedException.class, () -> guard.checkTradingAccess(8L));
        verify(accountRepository, never()).hasActiveStrategy(any());
    }
}
