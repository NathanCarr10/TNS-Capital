package com.neueda.leap.strategy.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface StrategyRepository extends JpaRepository<Strategy, Long> {

    List<Strategy> findByAccountIdAndStatusNotOrderByCreatedOnDesc(Long accountId, StrategyStatus status);

    List<Strategy> findByStatusNotOrderByCreatedOnDesc(StrategyStatus status);

    List<Strategy> findByAccountId(Long accountId);

    List<Strategy> findBySymbolAndStatus(String symbol, StrategyStatus status);

    long countByAccountIdAndStatus(Long accountId, StrategyStatus status);

    /**
     * Records what the strategy holds after one of its orders, without
     * touching the version, so it never conflicts with the owner pausing it.
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Strategy s SET s.position = :position WHERE s.id = :id")
    void updatePosition(@Param("id") Long id, @Param("position") Position position);
}
