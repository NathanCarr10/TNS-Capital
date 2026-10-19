package com.neueda.leap.repositories;

import com.neueda.leap.model.SupportMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {
    List<SupportMessage> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);
}