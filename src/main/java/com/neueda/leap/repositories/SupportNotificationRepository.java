package com.neueda.leap.repositories;

import com.neueda.leap.model.SupportNotification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportNotificationRepository extends JpaRepository<SupportNotification, Long> {
	long countByRecipientRoleAndReadAtIsNull(String recipientRole);
}