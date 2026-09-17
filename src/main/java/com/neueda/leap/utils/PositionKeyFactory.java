package com.neueda.leap.utils;

public final class PositionKeyFactory {
    private static final String KEY_SEPARATOR = "::";

    private PositionKeyFactory() {
        // Utility class; not instantiable
    }

    /**
     * Creates a position key from account ID and symbol.
     *
     * @param accountId the account ID
     * @param symbol    the normalized symbol
     * @return formatted position key
     */
    public static String createKey(Long accountId, String symbol) {
        return accountId + KEY_SEPARATOR + symbol;
    }
}
