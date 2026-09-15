package com.showtime.identity.exception;

/** Deliberately generic: never distinguishes "no such user" from "wrong password". */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
