package com.showtime.catalog.exception;

import java.util.UUID;

public class ScreenNotFoundException extends RuntimeException {

    public ScreenNotFoundException(UUID screenId) {
        super("screen not found: " + screenId);
    }
}
