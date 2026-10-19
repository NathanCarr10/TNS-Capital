package com.neueda.leap.services;

import com.neueda.leap.dtos.PositionResponse;
import com.neueda.leap.marketdata.PriceProvider;
import com.neueda.leap.marketdata.PriceQuote;
import com.neueda.leap.model.Position;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Values positions at the latest live prices.
 */
@Service
public class PositionValuationService {
    private final PriceProvider priceProvider;

    public PositionValuationService(PriceProvider priceProvider) {
        this.priceProvider = Objects.requireNonNull(priceProvider);
    }

    /**
     * Prices all the positions' symbols at once, so a portfolio costs one market data call.
     *
     * @return one response per position, in the same order; marketValue is null
     *         for a position with no live price
     */
    public List<PositionResponse> toResponses(List<Position> positions) {
        if (positions.isEmpty()) {
            return List.of();
        }
        List<String> symbols = positions.stream().map(Position::getSymbol).distinct().toList();
        Map<String, PriceQuote> prices = priceProvider.latestPrices(symbols);
        return positions.stream()
                .map(position -> toResponse(position, prices.get(position.getSymbol())))
                .toList();
    }

    public PositionResponse toResponse(Position position) {
        return toResponses(List.of(position)).get(0);
    }

    private static PositionResponse toResponse(Position position, PriceQuote quote) {
        BigDecimal marketValue = quote == null ? null
                : position.marketValue(quote.price()).setScale(2, RoundingMode.HALF_UP);
        return new PositionResponse(
                position.getAccountId(),
                position.getSymbol(),
                position.getQuantity(),
                position.getReservedQuantity(),
                position.getAvailableQuantity(),
                position.getAverageCost(),
                marketValue);
    }
}
