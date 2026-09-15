package com.showtime.booking.web;

import com.showtime.booking.domain.Booking;
import com.showtime.booking.security.CurrentUser;
import com.showtime.booking.service.BookingService;
import com.showtime.booking.web.dto.BookingResponse;
import com.showtime.booking.web.dto.CreateBookingRequest;
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
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping("/bookings")
    public ResponseEntity<BookingResponse> createBooking(@Valid @RequestBody CreateBookingRequest request) {
        UUID userId = CurrentUser.require().userId();
        Booking booking = bookingService.createBooking(userId, request.holdId());
        return ResponseEntity.status(HttpStatus.CREATED).body(BookingResponse.from(booking));
    }

    @GetMapping("/bookings/{bookingId}")
    public BookingResponse getBooking(@PathVariable("bookingId") UUID bookingId) {
        UUID userId = CurrentUser.require().userId();
        return BookingResponse.from(bookingService.getOwnedByUser(userId, bookingId));
    }

    @GetMapping("/bookings")
    public List<BookingResponse> myBookings() {
        UUID userId = CurrentUser.require().userId();
        return bookingService.listForUser(userId).stream().map(BookingResponse::from).toList();
    }
}
