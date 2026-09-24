package com.neueda.leap.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.neueda.leap.enums.AccountStatus;
import com.neueda.leap.exceptions.AccountNotActiveException;
import com.neueda.leap.exceptions.AccountNotFoundException;
import com.neueda.leap.model.Account;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.time.ClockTest;

@DisplayName("AccountService Test Suite")
class AccountServiceTest {
    private AccountService accountService;

    @Mock
    private AccountRepository accountRepository;

    private Account testAccount;
    private ClockTest testClock;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        accountService = new AccountService(accountRepository);
        testClock = new ClockTest(Instant.parse("2026-09-17T10:00:00Z"));
        testAccount = new Account("ACC001", "John Doe", new BigDecimal("50000.00"), testClock);
        testAccount.setId(1L);
    }

    @DisplayName("getAccountById Tests")
    @Nested
    class GetAccountByIdTests {
        @DisplayName("Should retrieve account by ID successfully")
        @Test
        void testGetAccountByIdSuccess() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));

            Account result = accountService.getAccountById(1L);

            assertNotNull(result);
            assertEquals(1L, result.getId());
            assertEquals("ACC001", result.getAccountId());
            assertEquals("John Doe", result.getHolderName());
            verify(accountRepository, times(1)).findById(1L);
        }

        @DisplayName("Should throw AccountNotFoundException when account not found")
        @Test
        void testGetAccountByIdNotFound() {
            when(accountRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(AccountNotFoundException.class, () -> accountService.getAccountById(999L),
                    "Should throw AccountNotFoundException for non-existent account");
            verify(accountRepository, times(1)).findById(999L);
        }

        @DisplayName("Should throw NullPointerException for null account ID")
        @Test
        void testGetAccountByIdNullAccountId() {
            assertThrows(NullPointerException.class, () -> accountService.getAccountById(null),
                    "Should throw NullPointerException for null account ID");
            verify(accountRepository, never()).findById(any());
        }
    }

    @DisplayName("findAccountById Tests")
    @Nested
    class FindAccountByIdTests {
        @DisplayName("Should return Optional with account when found")
        @Test
        void testFindAccountByIdSuccess() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));

            Optional<Account> result = accountService.findAccountById(1L);

            assertTrue(result.isPresent());
            assertEquals("ACC001", result.get().getAccountId());
            verify(accountRepository, times(1)).findById(1L);
        }

        @DisplayName("Should return empty Optional when account not found")
        @Test
        void testFindAccountByIdNotFound() {
            when(accountRepository.findById(999L)).thenReturn(Optional.empty());

            Optional<Account> result = accountService.findAccountById(999L);

            assertFalse(result.isPresent());
            verify(accountRepository, times(1)).findById(999L);
        }

        @DisplayName("Should throw NullPointerException for null account ID")
        @Test
        void testFindAccountByIdNullAccountId() {
            assertThrows(NullPointerException.class, () -> accountService.findAccountById(null),
                    "Should throw NullPointerException for null account ID");
            verify(accountRepository, never()).findById(any());
        }
    }

    @DisplayName("getCashBalance Tests")
    @Nested
    class GetCashBalanceTests {
        @DisplayName("Should retrieve cash balance successfully")
        @Test
        void testGetCashBalanceSuccess() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));

            BigDecimal balance = accountService.getCashBalance(1L);

            assertEquals(new BigDecimal("50000.00"), balance);
            verify(accountRepository, times(1)).findById(1L);
        }

        @DisplayName("Should throw AccountNotFoundException when account not found")
        @Test
        void testGetCashBalanceAccountNotFound() {
            when(accountRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(AccountNotFoundException.class, () -> accountService.getCashBalance(999L),
                    "Should throw AccountNotFoundException for non-existent account");
        }

        @DisplayName("Should return correct balance for various amounts")
        @ParameterizedTest(name = "Balance: {0}")
        @ValueSource(strings = { "0.00", "100.50", "999999.99", "0.01" })
        void testGetCashBalanceVariousAmounts(String amount) {
            Account account = new Account("ACC002", "Jane Doe", new BigDecimal(amount), testClock);
            account.setId(2L);
            when(accountRepository.findById(2L)).thenReturn(Optional.of(account));

            BigDecimal balance = accountService.getCashBalance(2L);

            assertEquals(new BigDecimal(amount), balance);
        }
    }

    @DisplayName("isAccountActive Tests")
    @Nested
    class IsAccountActiveTests {
        @DisplayName("Should return true for active account")
        @Test
        void testIsAccountActiveTrue() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));

            boolean isActive = accountService.isAccountActive(1L);

            assertTrue(isActive);
            verify(accountRepository, times(1)).findById(1L);
        }

        @DisplayName("Should return false for inactive account")
        @Test
        void testIsAccountActiveFalse() {
            testAccount.setStatus(AccountStatus.SUSPENDED);
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));

            boolean isActive = accountService.isAccountActive(1L);

            assertFalse(isActive);
            verify(accountRepository, times(1)).findById(1L);
        }

        @DisplayName("Should throw AccountNotFoundException when account not found")
        @Test
        void testIsAccountActiveAccountNotFound() {
            when(accountRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(AccountNotFoundException.class, () -> accountService.isAccountActive(999L),
                    "Should throw AccountNotFoundException for non-existent account");
        }
    }

    @DisplayName("validateAccountActive Tests")
    @Nested
    class ValidateAccountActiveTests {
        @DisplayName("Should pass validation for active account")
        @Test
        void testValidateAccountActiveSuccess() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));

            assertDoesNotThrow(() -> accountService.validateAccountActive(1L),
                    "Should not throw exception for active account");
            verify(accountRepository, times(1)).findById(1L);
        }

        @DisplayName("Should throw AccountNotActiveException for inactive account")
        @Test
        void testValidateAccountActiveThrowsException() {
            testAccount.setStatus(AccountStatus.SUSPENDED);
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));

            assertThrows(AccountNotActiveException.class, () -> accountService.validateAccountActive(1L),
                    "Should throw AccountNotActiveException for inactive account");
        }

        @DisplayName("Should throw AccountNotFoundException when account not found")
        @Test
        void testValidateAccountActiveAccountNotFound() {
            when(accountRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(AccountNotFoundException.class, () -> accountService.validateAccountActive(999L),
                    "Should throw AccountNotFoundException for non-existent account");
        }
    }

    @DisplayName("validateSufficientFunds Tests")
    @Nested
    class ValidateSufficientFundsTests {
        @DisplayName("Should pass validation with sufficient funds")
        @Test
        void testValidateSufficientFundsSuccess() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));

            assertDoesNotThrow(() -> accountService.validateSufficientFunds(1L, new BigDecimal("1000.00")),
                    "Should not throw exception when funds are sufficient");
        }

        @DisplayName("Should pass validation with exact balance amount")
        @Test
        void testValidateSufficientFundsExactBalance() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));

            assertDoesNotThrow(() -> accountService.validateSufficientFunds(1L, new BigDecimal("50000.00")),
                    "Should not throw exception when amount equals balance");
        }

        @DisplayName("Should throw IllegalArgumentException when insufficient funds")
        @Test
        void testValidateSufficientFundsInsufficientFunds() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));

            assertThrows(IllegalArgumentException.class,
                    () -> accountService.validateSufficientFunds(1L, new BigDecimal("60000.00")),
                    "Should throw IllegalArgumentException when funds are insufficient");
        }

        @DisplayName("Should throw NullPointerException for null amount")
        @Test
        void testValidateSufficientFundsNullAmount() {
            assertThrows(NullPointerException.class,
                    () -> accountService.validateSufficientFunds(1L, null),
                    "Should throw NullPointerException for null amount");
            verify(accountRepository, never()).findById(any());
        }

        @DisplayName("Should throw IllegalArgumentException for zero amount")
        @Test
        void testValidateSufficientFundsZeroAmount() {
            assertThrows(IllegalArgumentException.class,
                    () -> accountService.validateSufficientFunds(1L, BigDecimal.ZERO),
                    "Should throw IllegalArgumentException for zero amount");
            verify(accountRepository, never()).findById(any());
        }

        @DisplayName("Should throw IllegalArgumentException for negative amount")
        @Test
        void testValidateSufficientFundsNegativeAmount() {
            assertThrows(IllegalArgumentException.class,
                    () -> accountService.validateSufficientFunds(1L, new BigDecimal("-100.00")),
                    "Should throw IllegalArgumentException for negative amount");
            verify(accountRepository, never()).findById(any());
        }

        @DisplayName("Should throw AccountNotFoundException when account not found")
        @Test
        void testValidateSufficientFundsAccountNotFound() {
            when(accountRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(AccountNotFoundException.class,
                    () -> accountService.validateSufficientFunds(999L, new BigDecimal("1000.00")),
                    "Should throw AccountNotFoundException for non-existent account");
        }
    }

    @DisplayName("saveAccount Tests")
    @Nested
    class SaveAccountTests {
        @DisplayName("Should save account successfully")
        @Test
        void testSaveAccountSuccess() {
            assertDoesNotThrow(() -> accountService.saveAccount(testAccount),
                    "Should not throw exception when saving valid account");
            verify(accountRepository, times(1)).save(testAccount);
        }

        @DisplayName("Should throw NullPointerException for null account")
        @Test
        void testSaveAccountNullAccount() {
            assertThrows(NullPointerException.class, () -> accountService.saveAccount(null),
                    "Should throw NullPointerException for null account");
            verify(accountRepository, never()).save(any());
        }

        @DisplayName("Should call repository save method once")
        @Test
        void testSaveAccountCallsRepository() {
            accountService.saveAccount(testAccount);

            verify(accountRepository, times(1)).save(testAccount);
            verify(accountRepository, never()).findById(any());
        }
    }
}
