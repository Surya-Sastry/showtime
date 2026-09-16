package com.showtime.payment.web.dto;

import com.showtime.payment.domain.PaymentStatus;
import com.showtime.payment.domain.SimulatedPayment;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(String paymentId, UUID bookingId, PaymentStatus status, int amountCents, Instant createdAt) {

    public static PaymentResponse from(SimulatedPayment payment) {
        return new PaymentResponse(
                payment.getPaymentId(), payment.getBookingId(), payment.getStatus(), payment.getAmountCents(), payment.getCreatedAt());
    }
}
