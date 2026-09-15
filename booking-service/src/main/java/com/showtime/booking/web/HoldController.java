package com.showtime.booking.web;

import com.showtime.booking.domain.SeatHold;
import com.showtime.booking.security.CurrentUser;
import com.showtime.booking.service.HoldService;
import com.showtime.booking.web.dto.CreateHoldRequest;
import com.showtime.booking.web.dto.HoldResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HoldController {

    private final HoldService holdService;

    public HoldController(HoldService holdService) {
        this.holdService = holdService;
    }

    @PostMapping("/shows/{showId}/holds")
    public ResponseEntity<HoldResponse> createHold(
            @PathVariable("showId") UUID showId, @Valid @RequestBody CreateHoldRequest request) {
        UUID userId = CurrentUser.require().userId();
        SeatHold hold = holdService.createHold(userId, showId, request.seatIds());
        return ResponseEntity.status(HttpStatus.CREATED).body(HoldResponse.from(hold));
    }

    @DeleteMapping("/holds/{holdId}")
    public ResponseEntity<Void> releaseHold(@PathVariable("holdId") UUID holdId) {
        UUID userId = CurrentUser.require().userId();
        holdService.releaseHold(userId, holdId);
        return ResponseEntity.noContent().build();
    }
}
