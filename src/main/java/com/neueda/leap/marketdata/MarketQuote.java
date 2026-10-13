package com.neueda.leap.marketdata;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * An instrument's latest price and how far it has moved since the previous close.
 *
 * Every field but symbol is null when no live price is available, and
 * previousClose, change and changePercent are null when the previous close is unknown.
 *
 * @param symbol        the instrument symbol, e.g. AAPL
 * @param price         the price of the last trade
 * @param previousClose the close of the previous trading day
 * @param change        price minus previousClose
 * @param changePercent change as a percentage of previousClose, to 2 decimal places
 * @param tradedAt      when the last trade happened on the exchange
 */
public record MarketQuote(
        @Schema(requiredMode = REQUIRED) String symbol,
        @Schema(requiredMode = REQUIRED, nullable = true) BigDecimal price,
        @Schema(requiredMode = REQUIRED, nullable = true) BigDecimal previousClose,
        @Schema(requiredMode = REQUIRED, nullable = true) BigDecimal change,
        @Schema(requiredMode = REQUIRED, nullable = true) BigDecimal changePercent,
        @Schema(requiredMode = REQUIRED, nullable = true) Instant tradedAt) {

    static MarketQuote of(String symbol, PriceQuote quote, DailyCloses closes) {
        if (quote == null) {
            return new MarketQuote(symbol, null, null, null, null, null);
        }
        BigDecimal previousClose = closes == null ? null : closes.previousCloseFor(quote.tradedAt());
        if (previousClose == null || previousClose.signum() == 0) {
            return new MarketQuote(symbol, quote.price(), previousClose, null, null, quote.tradedAt());
        }
        BigDecimal change = quote.price().subtract(previousClose);
        BigDecimal changePercent = change.multiply(BigDecimal.valueOf(100))
                .divide(previousClose, 2, RoundingMode.HALF_UP);
        return new MarketQuote(symbol, quote.price(), previousClose, change, changePercent, quote.tradedAt());
    }
}
