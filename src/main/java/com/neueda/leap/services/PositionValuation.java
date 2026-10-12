package com.neueda.leap.services;

import com.neueda.leap.dtos.PositionResponse;
import com.neueda.leap.model.Instrument;
import com.neueda.leap.model.Position;
import com.neueda.leap.repositories.InstrumentRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Prices positions at the instruments' latest market price (FR-07).
 *
 * Market value is quantity x last price. It is null when the instrument has
 * no price yet, so a client can tell "unknown" apart from a value of zero.
 */
@Component
public class PositionValuation {
    private final InstrumentRepository instrumentRepository;

    public PositionValuation(InstrumentRepository instrumentRepository) {
        this.instrumentRepository = Objects.requireNonNull(instrumentRepository);
    }

    public PositionResponse toResponse(Position position) {
        BigDecimal price = instrumentRepository.findBySymbol(position.getSymbol())
                .map(Instrument::getLastPrice)
                .orElse(null);
        return toResponse(position, price);
    }

    /**
     * Prices several positions, reading each instrument once.
     */
    public List<PositionResponse> toResponses(List<Position> positions) {
        List<String> symbols = positions.stream().map(Position::getSymbol).distinct().toList();
        Map<String, Instrument> instruments = instrumentRepository.findAllById(symbols).stream()
                .collect(Collectors.toMap(Instrument::getSymbol, instrument -> instrument));
        return positions.stream()
                .map(position -> {
                    Instrument instrument = instruments.get(position.getSymbol());
                    return toResponse(position, instrument == null ? null : instrument.getLastPrice());
                })
                .toList();
    }

    private static PositionResponse toResponse(Position position, BigDecimal price) {
        return new PositionResponse(
                position.getAccountId(),
                position.getSymbol(),
                position.getQuantity(),
                position.getAverageCost(),
                price == null ? null : position.marketValue(price));
    }
}
