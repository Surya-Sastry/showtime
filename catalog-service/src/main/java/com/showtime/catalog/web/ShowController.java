package com.showtime.catalog.web;

import com.showtime.catalog.domain.Show;
import com.showtime.catalog.service.SeatMapService;
import com.showtime.catalog.service.ShowService;
import com.showtime.catalog.web.dto.CreateShowRequest;
import com.showtime.catalog.web.dto.SeatResponse;
import com.showtime.catalog.web.dto.ShowResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ShowController {

    private final ShowService showService;
    private final SeatMapService seatMapService;

    public ShowController(ShowService showService, SeatMapService seatMapService) {
        this.showService = showService;
        this.seatMapService = seatMapService;
    }

    @GetMapping("/shows/{showId}/seats")
    public List<SeatResponse> seatMap(@PathVariable("showId") UUID showId) {
        Show show = showService.getOrThrow(showId);
        return seatMapService.seatsFor(show).stream().map(SeatResponse::from).toList();
    }

    // Manager-only: enforced by SecurityConfig (hasRole("THEATER_MANAGER")),
    // not merely by this method existing behind auth middleware.
    @PostMapping("/shows")
    public ResponseEntity<ShowResponse> createShow(@Valid @RequestBody CreateShowRequest request) {
        Show show = showService.create(
                request.movieId(), request.screenId(), request.startsAt(), request.endsAt(), request.ticketPriceCents());
        return ResponseEntity.status(HttpStatus.CREATED).body(ShowResponse.from(show));
    }
}
