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
        when(accountRepository.findAccountIdByUsername("trader")).thenReturn(Optional.of(7L));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static void loginAs(String username, String... roles) {
        String[] authorities = java.util.Arrays.stream(roles).map(r -> "ROLE_" + r).toArray(String[]::new);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(username, null, authorities));
    }

    @Test
    @DisplayName("A user may access their own account")
    void ownerIsAllowed() {
        loginAs("trader", "USER");

        assertDoesNotThrow(() -> guard.checkAccess(7L));
        assertFalse(guard.isAdmin());
        assertEquals(Optional.of(7L), guard.currentAccountId());
    }

    @Test
    @DisplayName("A user may not access another user's account")
    void otherAccountIsDenied() {
        loginAs("trader", "USER");

        assertThrows(AccessDeniedException.class, () -> guard.checkAccess(8L));
    }

    @Test
    @DisplayName("A user without a linked account is denied every account")
    void userWithoutAccountIsDenied() {
        loginAs("stranger", "USER");
        when(accountRepository.findAccountIdByUsername("stranger")).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () -> guard.checkAccess(7L));
    }

    @Test
    @DisplayName("A null account ID is denied")
    void nullAccountIsDenied() {
        loginAs("trader", "USER");

        assertThrows(AccessDeniedException.class, () -> guard.checkAccess(null));
    }

    @Test
    @DisplayName("An admin may access every account without an ownership lookup")
    void adminIsAllowedEverywhere() {
        loginAs("admin", "ADMIN");

        assertTrue(guard.isAdmin());
        assertDoesNotThrow(() -> guard.checkAccess(8L));
        verify(accountRepository, never()).findAccountIdByUsername(any());
    }

    @Test
    @DisplayName("Without authentication nothing is accessible")
    void unauthenticatedIsDenied() {
        assertFalse(guard.isAdmin());
        assertEquals(Optional.empty(), guard.currentAccountId());
        assertThrows(AccessDeniedException.class, () -> guard.checkAccess(7L));
    }
}
