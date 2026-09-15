package com.showtime.booking.exception;

/**
 * Thrown when Redis's atomic acquire reports that at least one requested
 * seat is already held by someone else. See {@code RedisHoldService} for
 * why this check is race-free even with many concurrent requests for
 * overlapping seats.
 */
public class SeatUnavailableException extends RuntimeException {

    public SeatUnavailableException() {
        super("one or more requested seats are already held");
    }
}
