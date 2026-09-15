package com.showtime.booking.web;

import com.showtime.booking.catalog.CatalogUnavailableException;
import com.showtime.booking.catalog.InvalidSeatSelectionException;
import com.showtime.booking.catalog.ShowNotFoundException;
import com.showtime.booking.exception.BookingNotFoundException;
import com.showtime.booking.exception.BookingNotOwnedException;
import com.showtime.booking.exception.HoldExpiredException;
import com.showtime.booking.exception.HoldNotFoundException;
import com.showtime.booking.exception.HoldNotOwnedException;
import com.showtime.booking.exception.SeatUnavailableException;
import com.showtime.booking.payment.PaymentUnavailableException;
import com.showtime.booking.webhook.InvalidWebhookSignatureException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({HoldNotFoundException.class, BookingNotFoundException.class, ShowNotFoundException.class})
    public ResponseEntity<ApiError> handleNotFound(RuntimeException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), req);
    }

    @ExceptionHandler({HoldNotOwnedException.class, BookingNotOwnedException.class})
    public ResponseEntity<ApiError> handleForbidden(RuntimeException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), req);
    }

    @ExceptionHandler({SeatUnavailableException.class, HoldExpiredException.class})
    public ResponseEntity<ApiError> handleConflict(RuntimeException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), req);
    }

    @ExceptionHandler(InvalidSeatSelectionException.class)
    public ResponseEntity<ApiError> handleInvalidSeats(InvalidSeatSelectionException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), req);
    }

    @ExceptionHandler(InvalidWebhookSignatureException.class)
    public ResponseEntity<ApiError> handleInvalidSignature(InvalidWebhookSignatureException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), req);
    }

    @ExceptionHandler({CatalogUnavailableException.class, PaymentUnavailableException.class, CallNotPermittedException.class})
    public ResponseEntity<ApiError> handleDependencyUnavailable(RuntimeException ex, HttpServletRequest req) {
        // A controlled dependency failure, not a hang or an uncaught 500 —
        // per the README's Catalog-timeout/circuit-breaker requirement.
        return build(HttpStatus.SERVICE_UNAVAILABLE, "a downstream dependency is currently unavailable", req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "request failed validation", req);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest req) {
        return ResponseEntity.status(status)
                .body(new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, req.getRequestURI()));
    }
}
