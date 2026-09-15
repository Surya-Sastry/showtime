package com.showtime.booking.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.showtime.booking.service.BookingService;
import com.showtime.booking.webhook.InvalidWebhookSignatureException;
import com.showtime.booking.webhook.PaymentWebhookPayload;
import com.showtime.booking.webhook.WebhookSignatureVerifier;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives signed callbacks from the mock payment service. Deliberately not
 * behind JWT auth (the caller is a service, not a logged-in user); trust is
 * instead established per-request by {@code WebhookSignatureVerifier}
 * checking an HMAC-SHA-256 signature over the exact raw body received, so
 * this endpoint can stay in {@code SecurityConfig}'s permit-all list without
 * being an open door.
 */
@RestController
public class PaymentWebhookController {

    private static final String SIGNATURE_HEADER = "X-Signature";

    private final WebhookSignatureVerifier signatureVerifier;
    private final BookingService bookingService;
    private final ObjectMapper objectMapper;

    public PaymentWebhookController(
            WebhookSignatureVerifier signatureVerifier, BookingService bookingService, ObjectMapper objectMapper) {
        this.signatureVerifier = signatureVerifier;
        this.bookingService = bookingService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/internal/webhooks/payments")
    public ResponseEntity<Void> receivePaymentWebhook(
            @RequestHeader(SIGNATURE_HEADER) String signature, @RequestBody String rawBody) throws Exception {
        if (!signatureVerifier.isValid(rawBody.getBytes(StandardCharsets.UTF_8), signature)) {
            throw new InvalidWebhookSignatureException();
        }

        PaymentWebhookPayload payload = objectMapper.readValue(rawBody, PaymentWebhookPayload.class);
        bookingService.handlePaymentWebhook(payload.eventId(), payload.bookingId(), payload.outcome());
        return ResponseEntity.ok().build();
    }
}
