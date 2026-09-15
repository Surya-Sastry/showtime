package com.showtime.booking.web.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

public record CreateHoldRequest(@NotEmpty @Size(max = 8) Set<UUID> seatIds) {
}
