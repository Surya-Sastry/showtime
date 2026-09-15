package com.showtime.booking.webhook;

public class InvalidWebhookSignatureException extends RuntimeException {

    public InvalidWebhookSignatureException() {
        super("webhook signature verification failed");
    }
}
