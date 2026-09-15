package com.showtime.catalog.exception;

import java.util.UUID;

public class MovieNotFoundException extends RuntimeException {

    public MovieNotFoundException(UUID movieId) {
        super("movie not found: " + movieId);
    }
}
