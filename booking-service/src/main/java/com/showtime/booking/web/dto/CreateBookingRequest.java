package com.showtime.booking.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateBookingRequest(@NotNull UUID holdId) {
}
