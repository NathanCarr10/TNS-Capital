package com.neueda.leap.strategy.web.dto;

import com.neueda.leap.strategy.domain.Position;
import com.neueda.leap.strategy.domain.StrategyStatus;
import com.neueda.leap.strategy.marketdata.BarSeriesStore.SeriesView;
import com.neueda.leap.strategy.rules.RuleType;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A strategy, with what the service holds of its symbol's 1-minute bars.
 *
 * @param rule       the rule described for people
 * @param barsNeeded 1-minute bars the rule needs before it can signal
 * @param market     per source (LIVE, REPLAY): bars collected so far and the latest close
 */
public record StrategyResponse(
        Long id,
        Long accountId,
        String name,
        String symbol,
        RuleType ruleType,
        Map<String, Number> params,
        String rule,
        int quantity,
        StrategyStatus status,
        Position position,
        int barsNeeded,
        List<SeriesView> market,
        Instant createdOn,
        Instant updatedOn) {
}
