package com.showtime.booking.service;

import com.showtime.booking.catalog.CatalogClient;
import com.showtime.booking.domain.HoldStatus;
import com.showtime.booking.domain.SeatHold;
import com.showtime.booking.exception.HoldNotFoundException;
import com.showtime.booking.exception.HoldNotOwnedException;
import com.showtime.booking.exception.SeatUnavailableException;
import com.showtime.booking.redis.RedisHoldService;
import com.showtime.booking.repository.SeatHoldRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Orchestrates seat holds. The order of operations matters for
 * correctness: Catalog validation and the Redis acquire both happen BEFORE
 * anything is written to Postgres, so a rejected/failed hold never leaves a
 * stray "ACTIVE" audit row behind. If persisting the audit row fails after
 * a successful Redis acquire, the Redis reservation is rolled back so the
 * seat isn't stuck held with no corresponding record.
 */
@Service
public class HoldService {

    private final CatalogClient catalogClient;
    private final RedisHoldService redisHoldService;
    private final SeatHoldRepository seatHoldRepository;
    private final Duration holdTtl;

    public HoldService(
            CatalogClient catalogClient,
            RedisHoldService redisHoldService,
            SeatHoldRepository seatHoldRepository,
            @Value("${showtime.hold.ttl-seconds}") long holdTtlSeconds) {
        this.catalogClient = catalogClient;
        this.redisHoldService = redisHoldService;
        this.seatHoldRepository = seatHoldRepository;
        this.holdTtl = Duration.ofSeconds(holdTtlSeconds);
    }

    public SeatHold createHold(UUID userId, UUID showId, Set<UUID> seatIds) {
        // Fail fast on a nonexistent show/seat before touching Redis at all.
        catalogClient.getShow(showId);
        catalogClient.validateSeatsBelongToShow(showId, seatIds);

        UUID holdId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(holdTtl);

        boolean acquired = redisHoldService.acquire(showId, seatIds, holdId, holdTtl);
        if (!acquired) {
            throw new SeatUnavailableException();
        }

        SeatHold hold = new SeatHold(holdId, showId, userId, seatIds, now, expiresAt);
        try {
            return seatHoldRepository.save(hold);
        } catch (RuntimeException ex) {
            // The seat is reserved in Redis but we couldn't record it — undo
            // the reservation rather than leaving an unaccountable hold.
            redisHoldService.release(showId, seatIds, holdId);
            throw ex;
        }
    }

    public void releaseHold(UUID userId, UUID holdId) {
        SeatHold hold = seatHoldRepository.findById(holdId).orElseThrow(() -> new HoldNotFoundException(holdId));
        if (!hold.isOwnedBy(userId)) {
            throw new HoldNotOwnedException();
        }
        if (hold.getStatus() == HoldStatus.CONSUMED) {
            // A booking already exists from this hold; releasing it here
            // would free seats out from under a booking that still thinks
            // it owns them. Cancelling that booking is the correct path,
            // not releasing its hold directly.
            throw new IllegalStateException("hold " + holdId + " has already been consumed by a booking");
        }
        redisHoldService.release(hold.getShowId(), hold.getSeatIds(), holdId);
        hold.markReleased();
        seatHoldRepository.save(hold);
    }
}
