package com.showtime.booking.catalog;

import java.util.UUID;

public class ShowNotFoundException extends RuntimeException {

    public ShowNotFoundException(UUID showId) {
        super("show not found: " + showId);
    }
}
