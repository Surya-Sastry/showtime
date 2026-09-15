package com.showtime.booking.security;

import org.springframework.security.core.context.SecurityContextHolder;

/** Reads the authenticated principal that {@code JwtAuthenticationFilter} attached to this request. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static JwtVerifier.Principal require() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof JwtVerifier.Principal typed)) {
            throw new IllegalStateException("no authenticated principal on this request");
        }
        return typed;
    }
}
