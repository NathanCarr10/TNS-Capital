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
        StringBuilder sb = new StringBuilder();
        sb.append(accountId).append(KEY_SEPARATOR).append(symbol);
        return sb.toString();
    }
}
