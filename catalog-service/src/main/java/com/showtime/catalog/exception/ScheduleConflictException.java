package com.showtime.catalog.exception;

/**
 * Thrown when the database's exclusion constraint rejects an overlapping
 * show on the same screen. The service layer catches the low-level
 * {@code DataIntegrityViolationException} and translates it to this so the
 * API never leaks a raw SQL-state to the client.
 */
public class ScheduleConflictException extends RuntimeException {

    public ScheduleConflictException() {
        super("this screen already has a show scheduled in that time range");
    }
}
