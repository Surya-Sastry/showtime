package com.showtime.payment.exception;

public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(String paymentId) {
        super("payment not found: " + paymentId);
    }
}
