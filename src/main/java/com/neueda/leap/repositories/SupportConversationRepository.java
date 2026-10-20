package com.neueda.leap.repositories;

import com.neueda.leap.model.SupportConversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SupportConversationRepository extends JpaRepository<SupportConversation, UUID> {
    List<SupportConversation> findByCustomerEmailOrderByUpdatedAtDesc(String customerEmail);
    List<SupportConversation> findAllByOrderByUpdatedAtDesc();
}