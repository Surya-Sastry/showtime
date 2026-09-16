package com.showtime.payment.webhook;

import com.showtime.payment.domain.PaymentOutcome;
import java.util.UUID;

/**
 * The JSON shape Booking's {@code PaymentWebhookPayload} expects. Matched
 * by field name/shape only — this is the actual integration contract
 * between the two services, deliberately expressed as JSON rather than a
 * shared Java type.
 */
public record OutgoingWebhookPayload(String eventId, UUID bookingId, String providerPaymentId, PaymentOutcome outcome) {
}
