package com.neueda.leap.services;

import com.neueda.leap.dtos.PositionResponse;
import com.neueda.leap.marketdata.PriceProvider;
import com.neueda.leap.marketdata.PriceQuote;
import com.neueda.leap.model.Position;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * Prices positions at the latest live market price (FR-07), from the
 * PriceProvider (the Alpaca live price pool).
 *
 * Market value is quantity x live price, rounded to the cent. It is null when
 * there is no live price (e.g. Alpaca is not configured), so a client can
 * tell "unknown" apart from a value of zero.
 */
@Component
public class PositionValuation {
    private final PriceProvider priceProvider;

    public PositionValuation(PriceProvider priceProvider) {
        this.priceProvider = Objects.requireNonNull(priceProvider);
    }

    public PositionResponse toResponse(Position position) {
        BigDecimal price = priceProvider.latestPrice(position.getSymbol()).map(PriceQuote::price).orElse(null);
        return new PositionResponse(
                position.getAccountId(),
                position.getSymbol(),
                position.getQuantity(),
                position.getAverageCost(),
                price == null ? null : position.marketValue(price).setScale(2, RoundingMode.HALF_UP));
    }

    public List<PositionResponse> toResponses(List<Position> positions) {
        return positions.stream().map(this::toResponse).toList();
    }
}
