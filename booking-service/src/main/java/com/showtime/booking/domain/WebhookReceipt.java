package com.showtime.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One row per successfully processed webhook delivery. The UNIQUE constraint
 * on {@code event_id} (V1 migration) is what makes duplicate callback
 * delivery harmless: inserting this row is the first thing the handler does
 * inside the processing transaction, so a redelivered event fails fast on
 * the constraint instead of re-running confirmation/cancellation logic.
 */
@Entity
@Table(name = "webhook_receipts")
public class WebhookReceipt {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected WebhookReceipt() {
        // JPA
    }

    public WebhookReceipt(String eventId, UUID bookingId, Instant receivedAt) {
        this.eventId = eventId;
        this.bookingId = bookingId;
        this.receivedAt = receivedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
