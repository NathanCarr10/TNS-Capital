package com.neueda.leap.strategy.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StrategySignalRepository extends JpaRepository<StrategySignal, Long> {

    List<StrategySignal> findTop100ByStrategyIdOrderByCreatedOnDesc(Long strategyId);

    Optional<StrategySignal> findFirstByStrategyIdAndOutcomeOrderByCreatedOnDesc(Long strategyId,
            SignalOutcome outcome);

    List<StrategySignal> findTop100ByStrategyIdInAndCreatedOnAfterOrderByCreatedOnDesc(Collection<Long> strategyIds,
            Instant after);

    List<StrategySignal> findByStrategyIdInAndOutcome(Collection<Long> strategyIds, SignalOutcome outcome);
}
