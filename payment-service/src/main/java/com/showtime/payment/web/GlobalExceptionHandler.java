package com.showtime.payment.web;

import com.showtime.payment.exception.PaymentNotFoundException;
import com.showtime.payment.exception.PaymentOutcomeConflictException;
import com.showtime.payment.webhook.WebhookDeliveryException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(PaymentNotFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), req);
    }

    @ExceptionHandler(PaymentOutcomeConflictException.class)
    public ResponseEntity<ApiError> handleConflict(PaymentOutcomeConflictException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), req);
    }

    @ExceptionHandler(WebhookDeliveryException.class)
    public ResponseEntity<ApiError> handleDeliveryFailure(WebhookDeliveryException ex, HttpServletRequest req) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, "unable to deliver the webhook to Booking", req);
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
