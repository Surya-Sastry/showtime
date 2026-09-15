package com.showtime.catalog.web.dto;

import com.showtime.catalog.domain.SeatDefinition;
import java.util.UUID;

public record SeatResponse(UUID id, String seatLabel) {

    public static SeatResponse from(SeatDefinition seat) {
        return new SeatResponse(seat.getId(), seat.getSeatLabel());
    }
}
