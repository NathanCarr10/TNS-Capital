package com.neueda.leap.utils;

import java.util.Locale;

public final class InputNormalizer {

    private InputNormalizer() {
        // Utility class; not instantiable
    }

    /**
     * Normalizes a symbol by trimming whitespace and converting to uppercase.
     *
     * @param symbol the symbol to normalize
     * @return normalized symbol (uppercase, trimmed), or null if input is null
     */
    public static String normalize(String symbol) {
        return symbol == null ? null : symbol.trim().toUpperCase(Locale.ROOT);
    }
}
