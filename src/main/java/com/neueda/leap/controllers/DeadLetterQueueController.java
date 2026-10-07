package com.neueda.leap.controllers;

import com.neueda.leap.dtos.DeadLetterMessageDTO;
import com.neueda.leap.dtos.DLQStatistics;
import com.neueda.leap.enums.DLQStatus;
import com.neueda.leap.model.DeadLetterMessage;
import com.neueda.leap.repositories.DeadLetterMessageRepository;
import com.neueda.leap.services.DeadLetterService;
import com.neueda.leap.services.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST API controller for Dead-Letter Queue (DLQ) management.
 * 
 * Provides administrative endpoints for reviewing, replaying, and dismissing
 * failed order messages that were routed to the DLQ.
 * 
 * All endpoints require ADMIN role for access.
 */
@RestController
@RequestMapping("/api/v1/dlq")
@RequiredArgsConstructor
@Slf4j
public class DeadLetterQueueController {

    private final DeadLetterMessageRepository dlqRepository;
    private final DeadLetterService deadLetterService;
    private final OrderService orderService;

    /**
     * Retrieves DLQ messages with optional filtering.
     * 
     * Query Parameters:
     * - status: Filter by DLQ status (PENDING, RESOLVED, IGNORED) [optional, defaults to all]
     * - failureType: Filter by exception type [optional]
     * 
     * Returns: List of DLQ messages (newest first)
     * 
     * @param status      optional DLQ status filter
     * @param failureType optional failure type filter
     * @return ResponseEntity with list of DLQ message DTOs
     */
    @GetMapping("/messages")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<DeadLetterMessageDTO>> getDLQMessages(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String failureType) {

        List<DeadLetterMessage> messages;

        try {
            if (status != null && failureType != null) {
                DLQStatus dlqStatus = DLQStatus.valueOf(status.toUpperCase());
                messages = dlqRepository.findByStatusAndFailureTypeOrderByCreatedOnDesc(dlqStatus, failureType);
                log.info("Retrieved DLQ messages: status={}, failureType={}, count={}", status, failureType,
                        messages.size());
            } else if (status != null) {
                DLQStatus dlqStatus = DLQStatus.valueOf(status.toUpperCase());
                messages = dlqRepository.findByStatusOrderByCreatedOnDesc(dlqStatus);
                log.info("Retrieved DLQ messages: status={}, count={}", status, messages.size());
            } else if (failureType != null) {
                messages = dlqRepository.findByFailureTypeOrderByCreatedOnDesc(failureType);
                log.info("Retrieved DLQ messages: failureType={}, count={}", failureType, messages.size());
            } else {
                // No filters: return messages of every status
                messages = dlqRepository.findAllByOrderByCreatedOnDesc();
                log.info("Retrieved all DLQ messages: count={}", messages.size());
            }

            List<DeadLetterMessageDTO> dtos = messages.stream()
                    .map(this::mapToDTO)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(dtos);

        } catch (IllegalArgumentException ex) {
            log.warn("Invalid status filter: {}", status);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Retrieves a specific DLQ message by ID.
     * 
     * @param id the UUID of the DLQ message
     * @return ResponseEntity with the DLQ message DTO, or 404 if not found
     */
    @GetMapping("/messages/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DeadLetterMessageDTO> getDLQMessage(@PathVariable UUID id) {
        return dlqRepository.findById(id)
                .map(message -> {
                    log.info("Retrieved DLQ message: id={}, orderId={}", id, message.getOriginalOrderId());
                    return ResponseEntity.ok(mapToDTO(message));
                })
                .orElseGet(() -> {
                    log.warn("DLQ message not found: id={}", id);
                    return ResponseEntity.notFound().build();
                });
    }

    /**
     * Replays a DLQ message (admin-initiated retry).
     *
     * Attempts to re-process the original order event. If successful, the DLQ
     * message status is set to RESOLVED. If replay fails, the message remains
     * PENDING and retry count is incremented. Either way the request itself
     * succeeded, so the response is 200 with the message's updated state; the
     * status field tells the caller whether the replay worked.
     *
     * @param id the UUID of the DLQ message to replay
     * @return 200 with the updated DLQ message, or 404 if it does not exist
     */
    @PostMapping("/messages/{id}/replay")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DeadLetterMessageDTO> replayMessage(@PathVariable UUID id) {
        if (!dlqRepository.existsById(id)) {
            log.warn("DLQ message not found for replay: id={}", id);
            return ResponseEntity.notFound().build();
        }

        boolean success = deadLetterService.replayMessage(id, orderService);
        if (success) {
            log.info("DLQ message replay successful: id={}", id);
        } else {
            log.warn("DLQ message replay did not succeed: id={}", id);
        }

        return dlqRepository.findById(id)
                .map(message -> ResponseEntity.ok(mapToDTO(message)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Dismisses a DLQ message as non-recoverable.
     * 
     * Sets the message status to IGNORED, preventing future replays.
     * Optionally stores admin notes explaining the dismissal decision.
     * 
     * @param id         the UUID of the DLQ message to dismiss
     * @param adminNotes optional notes from the admin
     * @return ResponseEntity with 204 No Content on success
     */
    @DeleteMapping("/messages/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> dismissMessage(
            @PathVariable UUID id,
            @RequestParam(required = false) String adminNotes) {

        try {
            deadLetterService.dismissMessage(id, adminNotes);
            log.info("DLQ message dismissed: id={}, adminNotes={}", id, adminNotes);
            return ResponseEntity.noContent().build();

        } catch (Exception ex) {
            log.error("Error dismissing DLQ message: id={}, error={}", id, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Retrieves DLQ statistics.
     * 
     * Returns counts of messages by status to provide a high-level overview
     * of DLQ metrics.
     * 
     * @return ResponseEntity with DLQ statistics
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DLQStatistics> getDLQStatistics() {
        try {
            long pendingCount = dlqRepository.countByStatus(DLQStatus.PENDING);
            long resolvedCount = dlqRepository.countByStatus(DLQStatus.RESOLVED);
            long ignoredCount = dlqRepository.countByStatus(DLQStatus.IGNORED);
            long totalCount = pendingCount + resolvedCount + ignoredCount;

            DLQStatistics stats = new DLQStatistics(pendingCount, resolvedCount, ignoredCount, totalCount);
            log.info("DLQ statistics: pending={}, resolved={}, ignored={}, total={}",
                    pendingCount, resolvedCount, ignoredCount, totalCount);

            return ResponseEntity.ok(stats);

        } catch (Exception ex) {
            log.error("Error retrieving DLQ statistics: error={}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Maps a DeadLetterMessage entity to its DTO representation.
     * 
     * @param message the entity to map
     * @return the DTO
     */
    private DeadLetterMessageDTO mapToDTO(DeadLetterMessage message) {
        return new DeadLetterMessageDTO(
                message.getId(),
                message.getOriginalOrderId(),
                message.getFailureType(),
                message.getStatus().toString(),
                message.getRetryCount(),
                message.getCreatedOn(),
                message.getLastRetryOn(),
                message.getResolvedOn(),
                message.getAdminNotes(),
                message.getFailureReason());
    }
}
