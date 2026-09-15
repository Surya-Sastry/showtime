package com.showtime.identity.web;

import java.time.Instant;

/** One error shape for every endpoint in every Showtime service. */
public record ApiError(Instant timestamp, int status, String error, String message, String path) {
}
