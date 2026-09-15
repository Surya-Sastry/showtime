package com.showtime.booking.exception;

public class HoldExpiredException extends RuntimeException {

    public HoldExpiredException() {
        super("this hold has expired; request a new one");
    }
}
