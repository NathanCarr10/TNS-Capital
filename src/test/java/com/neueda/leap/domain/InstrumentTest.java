package com.neueda.leap.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;

import com.neueda.leap.model.Instrument;

@DisplayName("Instrument Test Suite")
class InstrumentTest {
    private Instrument instrument;

    @BeforeEach
    void setUp() {
        instrument = new Instrument("AAPL", "Apple Inc.", "EQUITY", "USD", true);
    }

    @DisplayName("Constructor initializes all fields correctly")
    @Test
    void testInstrumentConstructor() {
        assertEquals("AAPL", instrument.getSymbol(), "Symbol should match constructor argument");
        assertEquals("Apple Inc.", instrument.getName(), "Name should match constructor argument");
        assertEquals("EQUITY", instrument.getAssetClass(), "Asset class should match constructor argument");
        assertEquals("USD", instrument.getCurrency(), "Currency should match constructor argument");
        assertTrue(instrument.isTradable(), "Instrument should be tradable as specified");
    }

    @DisplayName("Tradability Status Tests")
    @Nested
    class TradabilityTests {
        @DisplayName("Tradable instrument returns true for isTradable()")
        @Test
        void testIsTradableTrue() {
            assertTrue(instrument.isTradable(), "Instrument should be tradable");
        }

        @DisplayName("Non-tradable instrument returns false for isTradable()")
        @Test
        void testIsTradableFalse() {
            Instrument nonTradable = new Instrument("DELISTED", "Delisted Corp", "EQUITY", "USD", false);
            assertFalse(nonTradable.isTradable(), "Instrument marked as non-tradable should return false");
        }
    }

    @DisplayName("Asset Class Support Tests")
    @Nested
    class AssetClassTests {
        @DisplayName("Instrument supports various asset classes")
        @ParameterizedTest(name = "Asset Class: {0}")
        @CsvSource({
                "BOND001, Corporate Bond, BOND, USD, true",
                "ES, E-mini S&P 500, FUTURE, USD, true",
                "AAPL_CALL, Apple Call Option, OPTION, USD, true",
                "AAPL, Apple Inc., EQUITY, USD, true"
        })
        void testMultipleAssetClasses(String symbol, String name, String assetClass, String currency,
                boolean tradable) {
            Instrument instrument = new Instrument(symbol, name, assetClass, currency, tradable);
            assertEquals(symbol, instrument.getSymbol(), "Symbol should match");
            assertEquals(assetClass, instrument.getAssetClass(), "Asset class should match");
            assertEquals(name, instrument.getName(), "Name should match");
        }
    }

    @DisplayName("Currency Support Tests")
    @Nested
    class CurrencyTests {
        @DisplayName("Instrument supports various currencies")
        @ParameterizedTest(name = "{0} instrument in {1}")
        @CsvSource({
                "SAP, SAP SE, EQUITY, EUR, true",
                "SHELL, Shell, EQUITY, GBP, true",
                "AAPL, Apple Inc., EQUITY, USD, true"
        })
        void testMultipleCurrencies(String symbol, String name, String assetClass, String currency, boolean tradable) {
            Instrument instrument = new Instrument(symbol, name, assetClass, currency, tradable);
            assertEquals(currency, instrument.getCurrency(), "Currency should match");
            assertEquals(symbol, instrument.getSymbol(), "Symbol should match");
        }
    }

    @Nested
    @DisplayName("updatePrice")
    class UpdatePrice {
        private final Instant at = Instant.parse("2026-10-12T09:00:00Z");

        @Test
        @DisplayName("has no price until one is set")
        void noPriceAtFirst() {
            Instrument instrument = new Instrument("ACME", "Acme Corp", "Equity", "USD", true);

            assertNull(instrument.getLastPrice());
            assertNull(instrument.getLastPriceOn());
        }

        @Test
        @DisplayName("stores the price rounded to cents and when it was observed")
        void storesPrice() {
            Instrument instrument = new Instrument("ACME", "Acme Corp", "Equity", "USD", true);

            instrument.updatePrice(new BigDecimal("12.345"), at);

            assertEquals(new BigDecimal("12.35"), instrument.getLastPrice());
            assertEquals(at, instrument.getLastPriceOn());
        }

        @ParameterizedTest
        @CsvSource({ "0", "-1.00" })
        @DisplayName("rejects a price that is not positive")
        void rejectsNonPositive(String price) {
            Instrument instrument = new Instrument("ACME", "Acme Corp", "Equity", "USD", true);

            assertThrows(IllegalArgumentException.class, () -> instrument.updatePrice(new BigDecimal(price), at));
        }

        @Test
        @DisplayName("rejects a missing price or time")
        void rejectsNulls() {
            Instrument instrument = new Instrument("ACME", "Acme Corp", "Equity", "USD", true);

            assertThrows(IllegalArgumentException.class, () -> instrument.updatePrice(null, at));
            assertThrows(IllegalArgumentException.class, () -> instrument.updatePrice(BigDecimal.ONE, null));
        }

        @Test
        @DisplayName("is kept by the copy constructor")
        void copied() {
            Instrument instrument = new Instrument("ACME", "Acme Corp", "Equity", "USD", true);
            instrument.updatePrice(new BigDecimal("12.00"), at);

            assertEquals(new BigDecimal("12.00"), new Instrument(instrument).getLastPrice());
        }
    }
}
