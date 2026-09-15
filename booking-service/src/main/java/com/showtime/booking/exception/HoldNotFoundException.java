package com.showtime.booking.exception;

import java.util.UUID;

public class HoldNotFoundException extends RuntimeException {

    public HoldNotFoundException(UUID holdId) {
        super("hold not found: " + holdId);
    }
}
