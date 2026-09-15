package com.showtime.catalog.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.UUID;

/**
 * No price is ever accepted from a booking client — this endpoint is the only
 * place ticket price is set, and only a THEATER_MANAGER can call it (enforced
 * by {@code SecurityConfig}, not just by this DTO's shape).
 */
public record CreateShowRequest(
        @NotNull UUID movieId,
        @NotNull UUID screenId,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @Positive int ticketPriceCents) {
}
