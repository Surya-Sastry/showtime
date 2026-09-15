package com.showtime.booking.catalog;

/**
 * Thrown when Catalog cannot be reached in time (deadline exceeded) or the
 * circuit breaker is open. Booking must surface this as a controlled
 * dependency error to the client rather than hanging or cascading — see the
 * README's "Catalog timeout" failure scenario.
 */
public class CatalogUnavailableException extends RuntimeException {

    public CatalogUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
