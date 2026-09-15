package com.showtime.booking.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "show_id", nullable = false)
    private UUID showId;

    @Column(name = "hold_id", nullable = false)
    private UUID holdId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private UUID idempotencyKey;

    @Column(name = "ticket_code")
    private String ticketCode;

    @Column(name = "total_price_cents", nullable = false)
    private int totalPriceCents;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ElementCollection
    @CollectionTable(name = "booking_requested_seats", joinColumns = @JoinColumn(name = "booking_id"))
    @Column(name = "seat_id")
    private Set<UUID> requestedSeatIds = new HashSet<>();

    protected Booking() {
        // JPA
    }

    public Booking(UUID userId, UUID showId, UUID holdId, Set<UUID> requestedSeatIds, int totalPriceCents, Instant now) {
        if (requestedSeatIds == null || requestedSeatIds.isEmpty()) {
            throw new IllegalArgumentException("a booking must request at least one seat");
        }
        if (totalPriceCents <= 0) {
            throw new IllegalArgumentException("totalPriceCents must be positive");
        }
        this.userId = userId;
        this.showId = showId;
        this.holdId = holdId;
        this.requestedSeatIds = new HashSet<>(requestedSeatIds);
        this.totalPriceCents = totalPriceCents;
        this.status = BookingStatus.PENDING_PAYMENT;
        this.idempotencyKey = UUID.randomUUID();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public boolean isOwnedBy(UUID candidateUserId) {
        return this.userId.equals(candidateUserId);
    }

    public void transitionTo(BookingStatus next, Instant now) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException("cannot transition booking " + id + " from " + status + " to " + next);
        }
        this.status = next;
        this.updatedAt = now;
    }

    public void confirmWithTicketCode(String ticketCode, Instant now) {
        transitionTo(BookingStatus.CONFIRMED, now);
        this.ticketCode = ticketCode;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getShowId() {
        return showId;
    }

    public UUID getHoldId() {
        return holdId;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getTicketCode() {
        return ticketCode;
    }

    public int getTotalPriceCents() {
        return totalPriceCents;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Set<UUID> getRequestedSeatIds() {
        return Set.copyOf(requestedSeatIds);
    }
}
