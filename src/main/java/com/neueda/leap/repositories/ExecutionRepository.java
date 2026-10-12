package com.neueda.leap.repositories;

import com.neueda.leap.model.Execution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ExecutionRepository extends JpaRepository<Execution, UUID> {
    Optional<Execution> findByOrderId(UUID orderId);
}
