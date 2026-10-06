package com.neueda.leap.controllers;

import com.neueda.leap.dtos.AccountResponse;
import com.neueda.leap.dtos.CreateAccountRequest;
import com.neueda.leap.dtos.OrderResponse;
import com.neueda.leap.dtos.PlaceOrderRequest;
import com.neueda.leap.dtos.UpdateAccountStatusRequest;
import com.neueda.leap.enums.AccountStatus;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.exceptions.AccountNotActiveException;
import com.neueda.leap.exceptions.AccountNotFoundException;
import com.neueda.leap.kafka.OrderEventPublisher;
import com.neueda.leap.model.Account;
import com.neueda.leap.model.Order;
import com.neueda.leap.model.Instrument;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.InstrumentRepository;
import com.neueda.leap.repositories.OrderHistoryRepository;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.repositories.PositionRepository;
import com.neueda.leap.security.AccountAccess;
import com.neueda.leap.services.AccountService;
import com.neueda.leap.services.OrderService;
import com.neueda.leap.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the admin/customer branches added to AccountController and
 * OrderController. The @PreAuthorize rules themselves are covered by AuthorizationIT.
 */
class AccountOwnershipControllerTest {

    private final Clock clock = () -> Instant.parse("2026-01-01T00:00:00Z");
    private final Authentication admin = new TestingAuthenticationToken("alice", "n/a", "ROLE_ADMIN");
    private final Authentication john = new TestingAuthenticationToken("john", "n/a", "ROLE_CUSTOMER");

    private AccountRepository accountRepository;
    private OrderRepository orderRepository;
    private OrderEventPublisher orderEventPublisher;
    private AccountAccess accountAccess;
    private AccountController accountController;
    private OrderController orderController;
    private Account johnsAccount;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        orderRepository = mock(OrderRepository.class);
        orderEventPublisher = mock(OrderEventPublisher.class);
        accountAccess = new AccountAccess(accountRepository, orderRepository, mock(OrderHistoryRepository.class));

        AccountService accountService = new AccountService(accountRepository, mock(PositionRepository.class),
                orderRepository, clock);
        accountController = new AccountController(accountService, accountAccess);
        InstrumentRepository instrumentRepository = mock(InstrumentRepository.class);
        when(instrumentRepository.findBySymbol("ACME"))
                .thenReturn(Optional.of(new Instrument("ACME", "Acme Corp", "EQUITY", "USD", true)));
        orderController = new OrderController(orderRepository, accountRepository, instrumentRepository,
                mock(OrderHistoryRepository.class), mock(OrderService.class), orderEventPublisher, accountAccess);

        johnsAccount = new Account("ACC-1", "John Doe", new BigDecimal("100.00"), clock);
        johnsAccount.setId(1L);
        johnsAccount.setOwnerUsername("john");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(johnsAccount));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private PlaceOrderRequest orderFor(Long accountId) {
        return new PlaceOrderRequest(accountId, "ACME", OrderSide.BUY, 1, new BigDecimal("10.00"), "key-1");
    }

    @Test
    void adminListsAllAccounts() {
        when(accountRepository.findAll()).thenReturn(List.of(johnsAccount));

        ResponseEntity<List<AccountResponse>> response = accountController.getAllAccounts(admin);

        assertEquals(1, response.getBody().size());
        verify(accountRepository, never()).findByOwnerUsername(any());
    }

    @Test
    void customerListsOnlyOwnedAccounts() {
        when(accountRepository.findByOwnerUsername("john")).thenReturn(List.of(johnsAccount));

        ResponseEntity<List<AccountResponse>> response = accountController.getAllAccounts(john);

        assertEquals("john", response.getBody().get(0).ownerUsername());
        verify(accountRepository, never()).findAll();
    }

    @Test
    void createAccountRecordsOwner() {
        CreateAccountRequest request = new CreateAccountRequest("ACC-2", "Nina", new BigDecimal("50.00"), "nina");

        ResponseEntity<AccountResponse> response = accountController.createAccount(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("nina", response.getBody().ownerUsername());
    }

    @Test
    void updateAccountStatusChangesStatus() {
        ResponseEntity<AccountResponse> response = accountController.updateAccountStatus(1L,
                new UpdateAccountStatusRequest(AccountStatus.SUSPENDED));

        assertEquals(AccountStatus.SUSPENDED, response.getBody().status());
    }

    @Test
    void updateAccountStatusRejectsMissingAccount() {
        when(accountRepository.findById(2L)).thenReturn(Optional.empty());
        UpdateAccountStatusRequest request = new UpdateAccountStatusRequest(AccountStatus.ACTIVE);

        assertThrows(AccountNotFoundException.class, () -> accountController.updateAccountStatus(2L, request));
    }

    @Test
    void customerCanPlaceOrderOnActiveAccount() {
        ResponseEntity<?> response = orderController.placeOrder(orderFor(1L), john);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        verify(orderEventPublisher).publishEvent(any(), eq(1L));
    }

    @Test
    void customerCannotPlaceOrderOnSuspendedAccount() {
        johnsAccount.setStatus(AccountStatus.SUSPENDED);
        PlaceOrderRequest request = orderFor(1L);

        assertThrows(AccountNotActiveException.class, () -> orderController.placeOrder(request, john));
        verifyNoInteractions(orderEventPublisher);
    }

    @Test
    void customerOrderForMissingAccountIsNotFound() {
        when(accountRepository.findById(2L)).thenReturn(Optional.empty());
        PlaceOrderRequest request = orderFor(2L);

        assertThrows(AccountNotFoundException.class, () -> orderController.placeOrder(request, john));
    }

    @Test
    void adminOrderSkipsActiveCheckAndIsValidatedAsynchronously() {
        johnsAccount.setStatus(AccountStatus.SUSPENDED);

        ResponseEntity<?> response = orderController.placeOrder(orderFor(1L), admin);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
    }

    @Test
    void adminListsAllOrdersCustomerListsOwn() {
        Order order = new Order(1L, "ACME", OrderSide.BUY, 1, new BigDecimal("10.00"), "key-1", clock);
        when(orderRepository.findAll()).thenReturn(List.of(order));
        when(accountRepository.findByOwnerUsername("john")).thenReturn(List.of(johnsAccount));
        when(orderRepository.findByAccountIdIn(List.of(1L))).thenReturn(List.of(order));

        ResponseEntity<List<OrderResponse>> adminView = orderController.getAllOrders(admin);
        ResponseEntity<List<OrderResponse>> customerView = orderController.getAllOrders(john);

        assertEquals(1, adminView.getBody().size());
        assertEquals(1, customerView.getBody().size());
        verify(orderRepository).findByAccountIdIn(List.of(1L));
    }
}
