package com.showtime.payment.domain;

/**
 * Deliberately duplicated (not shared via a common module) with Booking's
 * own {@code PaymentOutcome} enum of the same name/values. The two
 * services are only coupled through the JSON webhook contract, not through
 * shared Java types — a real payment provider wouldn't share code with its
 * merchants either.
 */
public enum PaymentOutcome {
    SUCCEEDED,
    FAILED
}
