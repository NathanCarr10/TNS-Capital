package com.neueda.leap.repositories;

import com.neueda.leap.enums.DLQStatus;
import com.neueda.leap.model.DeadLetterMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DeadLetterMessageRepository extends JpaRepository<DeadLetterMessage, UUID> {

    List<DeadLetterMessage> findByStatusOrderByCreatedOnDesc(DLQStatus status);

    List<DeadLetterMessage> findByStatusAndFailureTypeOrderByCreatedOnDesc(DLQStatus status, String failureType);

    @Query("SELECT m FROM DeadLetterMessage m WHERE m.status = :status AND m.createdOn >= :startDate ORDER BY m.createdOn DESC")
    List<DeadLetterMessage> findRecentMessages(@Param("status") DLQStatus status,
            @Param("startDate") Instant startDate);

    long countByStatus(DLQStatus status);

    long countByStatusAndFailureType(DLQStatus status, String failureType);
}
