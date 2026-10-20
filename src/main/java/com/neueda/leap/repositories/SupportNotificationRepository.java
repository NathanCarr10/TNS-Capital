package com.neueda.leap.repositories;

import com.neueda.leap.model.SupportNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SupportNotificationRepository extends JpaRepository<SupportNotification, Long> {
	long countByRecipientRoleAndReadAtIsNull(String recipientRole);

	@Modifying
	@Query("update SupportNotification n set n.readAt = CURRENT_TIMESTAMP where n.recipientRole = 'SUPPORT' and n.readAt is null")
	int markSupportNotificationsRead();
}