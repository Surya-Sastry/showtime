package com.showtime.booking.web.dto;

import com.showtime.booking.domain.Booking;
import com.showtime.booking.domain.BookingStatus;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        UUID showId,
        BookingStatus status,
        Set<UUID> seatIds,
        String ticketCode,
        int totalPriceCents,
        Instant createdAt,
        Instant updatedAt) {

    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getShowId(),
                booking.getStatus(),
                booking.getRequestedSeatIds(),
                booking.getTicketCode(),
                booking.getTotalPriceCents(),
                booking.getCreatedAt(),
                booking.getUpdatedAt());
    }
}
