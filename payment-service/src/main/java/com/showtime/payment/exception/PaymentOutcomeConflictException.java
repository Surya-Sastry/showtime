package com.showtime.payment.exception;

import com.showtime.payment.domain.PaymentStatus;

/** A settled payment cannot flip from SUCCEEDED to FAILED or vice versa. */
public class PaymentOutcomeConflictException extends RuntimeException {

    public PaymentOutcomeConflictException(String paymentId, PaymentStatus actualStatus) {
        super("payment " + paymentId + " is already settled as " + actualStatus);
    }
}
