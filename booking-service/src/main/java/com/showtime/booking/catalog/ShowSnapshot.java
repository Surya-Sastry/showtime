package com.showtime.booking.catalog;

import java.time.Instant;
import java.util.UUID;

/**
 * The subset of a Catalog show that Booking needs. In particular,
 * {@code ticketPriceCents} always comes from here, never from a client
 * request — Booking must never accept a price from the caller.
 */
public record ShowSnapshot(UUID showId, UUID movieId, UUID screenId, Instant startsAt, Instant endsAt, int ticketPriceCents) {
}
