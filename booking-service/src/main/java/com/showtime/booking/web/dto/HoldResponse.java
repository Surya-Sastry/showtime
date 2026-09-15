package com.showtime.booking.web.dto;

import com.showtime.booking.domain.SeatHold;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record HoldResponse(UUID id, UUID showId, Set<UUID> seatIds, Instant expiresAt) {

    public static HoldResponse from(SeatHold hold) {
        return new HoldResponse(hold.getId(), hold.getShowId(), hold.getSeatIds(), hold.getExpiresAt());
    }
}
