package com.showtime.booking.webhook;

import java.util.UUID;

public record PaymentWebhookPayload(
        String eventId, UUID bookingId, String providerPaymentId, PaymentOutcome outcome) {
}
