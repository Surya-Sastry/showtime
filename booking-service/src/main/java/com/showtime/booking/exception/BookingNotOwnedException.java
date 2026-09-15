package com.showtime.booking.exception;

public class BookingNotOwnedException extends RuntimeException {

    public BookingNotOwnedException() {
        super("this booking does not belong to the current user");
    }
}
