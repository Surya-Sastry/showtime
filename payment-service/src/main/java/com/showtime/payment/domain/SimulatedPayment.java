package com.showtime.payment.domain;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * An in-memory simulated payment attempt. This service intentionally owns
 * no database — per the README, "no real money" and "learning scale" — the
 * durable record of a payment attempt lives in Booking's own
 * {@code payment_attempts} table (Booking owns that fact; Payment is just
 * the simulator that produces the callback). If this process restarts,
 * in-flight simulated payments are lost, which is fine for what this
 * service is for.
 */
public class SimulatedPayment {

    private final String paymentId;
    private final UUID bookingId;
    private final int amountCents;
    private final Instant createdAt;
    private final AtomicReference<PaymentStatus> status;

    public SimulatedPayment(UUID bookingId, int amountCents) {
        this.paymentId = "pay-" + UUID.randomUUID();
        this.bookingId = bookingId;
        this.amountCents = amountCents;
        this.createdAt = Instant.now();
        this.status = new AtomicReference<>(PaymentStatus.CREATED);
    }

    /** @return true if this call actually changed the status (first completion wins); false if already completed. */
    public boolean complete(PaymentStatus outcome) {
        if (outcome == PaymentStatus.CREATED) {
            throw new IllegalArgumentException("cannot complete a payment into CREATED");
        }
        return status.compareAndSet(PaymentStatus.CREATED, outcome);
    }

    public String getPaymentId() {
        return paymentId;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public int getAmountCents() {
        return amountCents;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public PaymentStatus getStatus() {
        return status.get();
    }
}
