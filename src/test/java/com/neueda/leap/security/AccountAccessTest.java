package com.neueda.leap.security;

import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.model.Account;
import com.neueda.leap.model.Order;
import com.neueda.leap.model.OrderHistory;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.OrderHistoryRepository;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountAccessTest {

    private final Clock clock = () -> Instant.parse("2026-01-01T00:00:00Z");
    private AccountRepository accountRepository;
    private OrderRepository orderRepository;
    private OrderHistoryRepository orderHistoryRepository;
    private AccountAccess accountAccess;
    private Account johnsAccount;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        orderRepository = mock(OrderRepository.class);
        orderHistoryRepository = mock(OrderHistoryRepository.class);
        accountAccess = new AccountAccess(accountRepository, orderRepository, orderHistoryRepository);

        johnsAccount = new Account("ACC-1", "John Doe", new BigDecimal("100.00"), clock);
        johnsAccount.setId(1L);
        johnsAccount.setOwnerUsername("john");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(johnsAccount));
        when(accountRepository.findById(2L)).thenReturn(Optional.empty());
    }

    private Authentication user(String name, String... roles) {
        return new TestingAuthenticationToken(name, "n/a",
                java.util.Arrays.stream(roles).map(r -> "ROLE_" + r).toArray(String[]::new));
    }

    @Test
    void adminIsRecognisedFromRoleAuthority() {
        assertTrue(accountAccess.isAdmin(user("alice", "ADMIN")));
        assertFalse(accountAccess.isAdmin(user("john", "CUSTOMER")));
    }

    @Test
    void ownerCanAccessOwnAccount() {
        assertTrue(accountAccess.ownsAccount(user("john", "CUSTOMER"), 1L));
    }

    @Test
    void otherCustomerCannotAccessAccount() {
        assertFalse(accountAccess.ownsAccount(user("nina", "CUSTOMER"), 1L));
    }

    @Test
    void missingAccountIsNotOwned() {
        assertFalse(accountAccess.ownsAccount(user("john", "CUSTOMER"), 2L));
    }

    @Test
    void accountWithoutOwnerIsNotOwnedByAnyone() {
        johnsAccount.setOwnerUsername(null);
        assertFalse(accountAccess.ownsAccount(user("john", "CUSTOMER"), 1L));
    }

    @Test
    void orderOwnershipFollowsItsAccount() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(1L, "ACME", OrderSide.BUY, 1, new BigDecimal("1.00"), "key-1", clock);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertTrue(accountAccess.ownsOrder(user("john", "CUSTOMER"), orderId));
        assertFalse(accountAccess.ownsOrder(user("nina", "CUSTOMER"), orderId));
        assertFalse(accountAccess.ownsOrder(user("john", "CUSTOMER"), UUID.randomUUID()));
    }

    @Test
    void orderHistoryOwnershipFollowsItsAccount() {
        UUID orderId = UUID.randomUUID();
        OrderHistory history = mock(OrderHistory.class);
        when(history.getAccountId()).thenReturn(1L);
        when(orderHistoryRepository.findByOrderId(orderId)).thenReturn(Optional.of(history));

        assertTrue(accountAccess.ownsOrderHistory(user("john", "CUSTOMER"), orderId));
        assertFalse(accountAccess.ownsOrderHistory(user("nina", "CUSTOMER"), orderId));
        assertFalse(accountAccess.ownsOrderHistory(user("john", "CUSTOMER"), UUID.randomUUID()));
    }

    @Test
    void nullInputsAreNeverOwned() {
        assertFalse(accountAccess.isAdmin(null));
        assertFalse(accountAccess.ownsAccount(null, 1L));
        assertFalse(accountAccess.ownsAccount(user("john", "CUSTOMER"), null));
        assertFalse(accountAccess.ownsOrder(user("john", "CUSTOMER"), null));
        assertFalse(accountAccess.ownsOrderHistory(user("john", "CUSTOMER"), null));
    }

    @Test
    void ownedAccountsLooksUpByTokenSubject() {
        when(accountRepository.findByOwnerUsername("john")).thenReturn(List.of(johnsAccount));

        assertEquals(List.of(johnsAccount), accountAccess.ownedAccounts(user("john", "CUSTOMER")));
    }
}
