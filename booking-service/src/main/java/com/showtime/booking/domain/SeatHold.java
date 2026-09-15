package com.showtime.booking.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The durable audit record for a seat hold. Redis (see {@code RedisHoldService})
 * is the fast-path, TTL-expiring source of truth for "is this seat held right
 * now"; this row exists so hold history survives a Redis restart and so
 * booking creation can check hold ownership against a record the client
 * cannot forge.
 *
 * <p>The ID is application-assigned (not {@code @GeneratedValue}) precisely
 * because {@code HoldService} needs the same UUID to exist as the Redis hold
 * key BEFORE this row is ever persisted — Redis is where the seat is
 * actually reserved; this row is written only after that reservation
 * succeeds, using the same ID so the two stay correlatable.
 */
@Entity
@Table(name = "seat_holds")
public class SeatHold {

    @Id
    private UUID id;

    @Column(name = "show_id", nullable = false)
    private UUID showId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HoldStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @ElementCollection
    @CollectionTable(name = "hold_seats", joinColumns = @JoinColumn(name = "hold_id"))
    @Column(name = "seat_id")
    private Set<UUID> seatIds = new HashSet<>();

    protected SeatHold() {
        // JPA
    }

    public SeatHold(UUID id, UUID showId, UUID userId, Set<UUID> seatIds, Instant createdAt, Instant expiresAt) {
        if (seatIds == null || seatIds.isEmpty()) {
            throw new IllegalArgumentException("a hold must cover at least one seat");
        }
        this.id = id;
        this.showId = showId;
        this.userId = userId;
        this.seatIds = new HashSet<>(seatIds);
        this.status = HoldStatus.ACTIVE;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public boolean isOwnedBy(UUID candidateUserId) {
        return this.userId.equals(candidateUserId);
    }

    public boolean isActive(Instant now) {
        return status == HoldStatus.ACTIVE && now.isBefore(expiresAt);
    }

    /** Only a currently-ACTIVE hold can be consumed by a new booking. */
    public void markConsumed() {
        if (this.status != HoldStatus.ACTIVE) {
            throw new IllegalStateException("hold " + id + " is no longer ACTIVE (status=" + status + ")");
        }
        this.status = HoldStatus.CONSUMED;
    }

    /**
     * Releasing is allowed from ACTIVE (explicit user release) or CONSUMED
     * (compensation after a booking that consumed this hold was cancelled),
     * but not from an already-terminal RELEASED/EXPIRED state.
     */
    public void markReleased() {
        if (this.status == HoldStatus.RELEASED || this.status == HoldStatus.EXPIRED) {
            return;
        }
        this.status = HoldStatus.RELEASED;
    }

    public void markExpired() {
        this.status = HoldStatus.EXPIRED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getShowId() {
        return showId;
    }

    public UUID getUserId() {
        return userId;
    }

    public HoldStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Set<UUID> getSeatIds() {
        return Set.copyOf(seatIds);
    }
}
