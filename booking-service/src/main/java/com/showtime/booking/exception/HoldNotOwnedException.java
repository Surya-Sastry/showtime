package com.showtime.booking.exception;

/** Enforces ownership on every hold operation, per the README security requirement. */
public class HoldNotOwnedException extends RuntimeException {

    public HoldNotOwnedException() {
        super("this hold does not belong to the current user");
    }
}
