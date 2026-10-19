package com.neueda.leap.exceptions;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.kafka.events.OrderEvent;

/**
 * Every custom exception extends TradingException directly and carries the
 * error code from the spec's catalogue (§21).
 */
class TradingExceptionHierarchyTest {

    static Stream<Arguments> exceptions() {
        Throwable cause = new RuntimeException("down");
        return Stream.of(
                Arguments.of(new AccountNotFoundException("x"), "ACC-404"),
                Arguments.of(new InstrumentNotFoundException("x"), "INS-404"),
                Arguments.of(new OrderNotFoundException("x"), "ORD-404"),
                Arguments.of(new PositionNotFoundException("x"), "POS-404"),
                Arguments.of(new NewsletterNotFoundException("x"), "NWS-404"),
                Arguments.of(new AccountNotActiveException("x"), "ACC-403"),
                Arguments.of(new InsufficientFundsException("x"), "ORD-400"),
                Arguments.of(new InsufficientHoldingsException("x"), "ORD-409"),
                Arguments.of(new AccountAlreadyExistsException("x"), "ACC-409"),
                Arguments.of(new AccountDeletionConflictException("x"), "ACC-409"),
                Arguments.of(new DuplicateInstrumentException("x"), "INS-409"),
                Arguments.of(new DuplicateOrderException("x"), "ORD-409"),
                Arguments.of(new OrderCancellationConflictException("x"), "ORD-409"),
                Arguments.of(new LimitNotMarketableException("x"), "ORD-422"),
                Arguments.of(new PriceUnavailableException("x"), "PRC-503"),
                Arguments.of(new MarketDataUnavailableException("x"), "MKT-503"),
                Arguments.of(new OrderPublishException("x", cause), "SYS-503"),
                Arguments.of(new BrokerUnavailableException("x", cause), "BRK-503"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("exceptions")
    @DisplayName("Each exception extends TradingException and has its spec error code")
    void testErrorCode(TradingException exception, String errorCode) {
        assertEquals(TradingException.class, exception.getClass().getSuperclass(),
                "Extends TradingException directly, keeping the tree within Sonar's depth limit");
        assertInstanceOf(RuntimeException.class, exception, "Must stay unchecked");
        assertEquals(errorCode, exception.getErrorCode());
        assertEquals("x", exception.getMessage());
    }

    @Test
    @DisplayName("A wrapped order failure keeps the error code of the failure it wraps")
    void testNonRetryableKeepsCauseCode() {
        var wrapped = new NonRetryableOrderException("Account not found: 7", new AccountNotFoundException("7"));

        assertEquals("ACC-404", wrapped.getErrorCode());
    }

    @Test
    @DisplayName("A settlement failure keeps the error code of the rule that refused the fill")
    void testSettlementKeepsCauseCode() {
        OrderEvent event = new OrderEvent(UUID.randomUUID(), 1L, "ACME", OrderSide.BUY, 10,
                new BigDecimal("25.50"), Instant.parse("2026-10-16T10:00:00Z"), "key-1");

        var settlement = new SettlementException(event, new InsufficientFundsException("Insufficient funds"));

        assertEquals("ORD-400", settlement.getErrorCode());
    }

    @Test
    @DisplayName("Wrapping an unexpected error gives SYS-500")
    void testWrappedUnexpectedCause() {
        var wrapped = new NonRetryableOrderException("boom", new IllegalStateException("boom"));

        assertEquals("SYS-500", wrapped.getErrorCode());
    }
}
