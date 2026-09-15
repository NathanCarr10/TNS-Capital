package com.neueda.leap.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InstrumentTest {
    private Instrument instrument;

    @BeforeEach
    void setUp() {
        instrument = new Instrument("AAPL", "Apple Inc.", "EQUITY", "USD", true);
    }

    @Test
    void testInstrumentConstructor() {
        // ARRANGE: instrument created in setUp
        
        // ACT & ASSERT: verify initialization
        assertEquals("AAPL", instrument.getSymbol());
        assertEquals("Apple Inc.", instrument.getName());
        assertEquals("EQUITY", instrument.getAssetClass());
        assertEquals("USD", instrument.getCurrency());
        assertTrue(instrument.isTradable());
    }

    @Test
    void testIsTradableTrue() {
        // ARRANGE: tradable instrument created in setUp
        
        // ACT & ASSERT
        assertTrue(instrument.isTradable());
    }

    @Test
    void testIsTradableFalse() {
        // ARRANGE
        Instrument nonTradable = new Instrument("DELISTED", "Delisted Corp", "EQUITY", "USD", false);
        
        // ACT & ASSERT
        assertFalse(nonTradable.isTradable());
    }

    @Test
    void testInstrumentWithDifferentAssetClasses() {
        // ARRANGE
        Instrument bond = new Instrument("BOND001", "Corporate Bond", "BOND", "USD", true);
        Instrument future = new Instrument("ES", "E-mini S&P 500", "FUTURE", "USD", true);
        Instrument option = new Instrument("AAPL_CALL", "Apple Call Option", "OPTION", "USD", true);
        
        // ACT & ASSERT
        assertEquals("BOND", bond.getAssetClass());
        assertEquals("FUTURE", future.getAssetClass());
        assertEquals("OPTION", option.getAssetClass());
    }

    @Test
    void testInstrumentWithDifferentCurrencies() {
        // ARRANGE
        Instrument eurInstrument = new Instrument("SAP", "SAP SE", "EQUITY", "EUR", true);
        Instrument gbpInstrument = new Instrument("SHELL", "Shell", "EQUITY", "GBP", true);
        
        // ACT & ASSERT
        assertEquals("EUR", eurInstrument.getCurrency());
        assertEquals("GBP", gbpInstrument.getCurrency());
    }
}
