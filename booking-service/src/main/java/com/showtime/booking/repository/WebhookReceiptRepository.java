package com.showtime.booking.repository;

import com.showtime.booking.domain.WebhookReceipt;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookReceiptRepository extends JpaRepository<WebhookReceipt, UUID> {

    boolean existsByEventId(String eventId);
}
