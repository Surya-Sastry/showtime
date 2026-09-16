package com.showtime.payment.service;

import com.showtime.payment.domain.PaymentOutcome;
import com.showtime.payment.domain.PaymentStatus;
import com.showtime.payment.domain.SimulatedPayment;
import com.showtime.payment.exception.PaymentNotFoundException;
import com.showtime.payment.exception.PaymentOutcomeConflictException;
import com.showtime.payment.repository.PaymentStore;
import com.showtime.payment.webhook.BookingWebhookClient;
import com.showtime.payment.webhook.OutgoingWebhookPayload;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    private final PaymentStore paymentStore;
    private final BookingWebhookClient webhookClient;

    public PaymentService(PaymentStore paymentStore, BookingWebhookClient webhookClient) {
        this.paymentStore = paymentStore;
        this.webhookClient = webhookClient;
    }

    public SimulatedPayment createPayment(UUID bookingId, int amountCents) {
        return paymentStore.save(new SimulatedPayment(bookingId, amountCents));
    }

    public SimulatedPayment getPayment(String paymentId) {
        return paymentStore.findById(paymentId).orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

    /**
     * Settles a simulated payment and delivers the signed webhook to
     * Booking. The event ID is derived deterministically from
     * {@code (paymentId, outcome)}, so calling this twice with the SAME
     * outcome (e.g. to exercise the README's "payment callback delivered
     * twice" scenario) redelivers the identical event ID rather than
     * minting a new one — Booking's own idempotency fence is what makes
     * the redelivery harmless, exactly as it would be for a real
     * at-least-once webhook provider.
     */
    public SimulatedPayment complete(String paymentId, PaymentOutcome outcome) {
        SimulatedPayment payment = getPayment(paymentId);
        PaymentStatus targetStatus = toStatus(outcome);

        boolean transitioned = payment.complete(targetStatus);
        if (!transitioned && payment.getStatus() != targetStatus) {
            throw new PaymentOutcomeConflictException(paymentId, payment.getStatus());
        }

        String eventId = payment.getPaymentId() + "-" + outcome;
        webhookClient.deliver(new OutgoingWebhookPayload(eventId, payment.getBookingId(), payment.getPaymentId(), outcome));
        return payment;
    }

    private static PaymentStatus toStatus(PaymentOutcome outcome) {
        return outcome == PaymentOutcome.SUCCEEDED ? PaymentStatus.SUCCEEDED : PaymentStatus.FAILED;
    }
}
