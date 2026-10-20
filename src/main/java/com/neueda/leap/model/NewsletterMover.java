package com.neueda.leap.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One of the week's biggest movers in a newsletter issue (an element of the
 * gainers or losers JSONB column).
 *
 * @param symbol     the instrument symbol, e.g. AAPL
 * @param name       the instrument name when the issue was published
 * @param startClose close of the last trading day before the week
 * @param endClose   close of the latest trading day of the week
 * @param changePct  change from startClose to endClose in percent, 2 decimal places
 */
public record NewsletterMover(String symbol, String name, BigDecimal startClose, BigDecimal endClose,
        BigDecimal changePct) {

    public NewsletterMover {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(startClose, "startClose");
        Objects.requireNonNull(endClose, "endClose");
        Objects.requireNonNull(changePct, "changePct");
    }
}
