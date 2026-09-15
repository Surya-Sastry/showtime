package com.showtime.catalog.web.dto;

import com.showtime.catalog.domain.Show;
import java.time.Instant;
import java.util.UUID;

public record ShowResponse(
        UUID id,
        UUID movieId,
        UUID screenId,
        String theaterName,
        String screenName,
        Instant startsAt,
        Instant endsAt,
        int ticketPriceCents) {

    public static ShowResponse from(Show show) {
        return new ShowResponse(
                show.getId(),
                show.getMovie().getId(),
                show.getScreen().getId(),
                show.getScreen().getTheater().getName(),
                show.getScreen().getName(),
                show.getStartsAt(),
                show.getEndsAt(),
                show.getTicketPriceCents());
    }
}
