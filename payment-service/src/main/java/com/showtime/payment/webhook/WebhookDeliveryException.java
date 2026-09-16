package com.showtime.payment.webhook;

public class WebhookDeliveryException extends RuntimeException {

    public WebhookDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
