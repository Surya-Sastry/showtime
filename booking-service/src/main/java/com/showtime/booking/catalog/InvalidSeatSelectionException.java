package com.showtime.booking.catalog;

/**
 * Thrown when one or more requested seat_ids don't belong to the show's
 * screen, per Catalog's rejection. Booking deliberately trusts Catalog as
 * the authority on which seats exist; it doesn't keep its own copy.
 */
public class InvalidSeatSelectionException extends RuntimeException {

    public InvalidSeatSelectionException(String message) {
        super(message);
    }
}
