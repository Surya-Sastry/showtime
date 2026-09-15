package com.showtime.booking.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Mirrors the state machine documented in the project README:
 *
 * <pre>
 * PENDING_PAYMENT ── success callback ──► CONFIRMED
 *        │
 *        ├── failed callback ───────────► CANCELLED
 *        └── timeout/expiry ────────────► EXPIRED
 * </pre>
 *
 * {@code REQUIRES_REVIEW} is the one addition beyond the README diagram: it
 * is reached from {@code PENDING_PAYMENT} when payment succeeded but the
 * confirmation transaction lost the seat to someone else (the hold expired
 * and another booking got there first). Money moved but seats didn't, so
 * this is a case for manual reconciliation/refund, not a silent double-book
 * and not a silent success.
 */
public enum BookingStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    CANCELLED,
    EXPIRED,
    REQUIRES_REVIEW;

    private static final Set<BookingStatus> TERMINAL =
            EnumSet.of(CONFIRMED, CANCELLED, EXPIRED, REQUIRES_REVIEW);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    /**
     * Enforces the transition table in the domain layer, not only in
     * controllers, per the README's explicit instruction.
     */
    public boolean canTransitionTo(BookingStatus next) {
        if (this == PENDING_PAYMENT) {
            return next == CONFIRMED || next == CANCELLED || next == EXPIRED || next == REQUIRES_REVIEW;
        }
        // All other states are terminal; no further transitions.
        return false;
    }
}
