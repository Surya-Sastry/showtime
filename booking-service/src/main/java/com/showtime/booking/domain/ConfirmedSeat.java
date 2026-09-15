package com.showtime.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * The actual authority against double-booking. A row here exists if and
 * only if a seat has been confirmed for a show, and the database-level
 * {@code UNIQUE(show_id, seat_id)} constraint (see the V1 migration) is what
 * makes it physically impossible for two bookings to both insert a row for
 * the same seat — not the application code that inserts it.
 */
@Entity
@Table(name = "booking_seats")
public class ConfirmedSeat {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "show_id", nullable = false)
    private UUID showId;

    @Column(name = "seat_id", nullable = false)
    private UUID seatId;

    @Column(name = "confirmed_at", nullable = false)
    private Instant confirmedAt;

    protected ConfirmedSeat() {
        // JPA
    }

    public ConfirmedSeat(UUID bookingId, UUID showId, UUID seatId, Instant confirmedAt) {
        this.bookingId = bookingId;
        this.showId = showId;
        this.seatId = seatId;
        this.confirmedAt = confirmedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public UUID getShowId() {
        return showId;
    }

    public UUID getSeatId() {
        return seatId;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }
}
