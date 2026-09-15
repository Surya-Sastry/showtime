package com.showtime.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_attempts")
public class PaymentAttempt {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "provider_payment_id", nullable = false)
    private String providerPaymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentAttemptStatus status;

    @Column(name = "amount_cents", nullable = false)
    private int amountCents;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PaymentAttempt() {
        // JPA
    }

    public PaymentAttempt(UUID bookingId, String providerPaymentId, int amountCents, Instant createdAt) {
        this.bookingId = bookingId;
        this.providerPaymentId = providerPaymentId;
        this.amountCents = amountCents;
        this.status = PaymentAttemptStatus.CREATED;
        this.createdAt = createdAt;
    }

    public void markSucceeded() {
        this.status = PaymentAttemptStatus.SUCCEEDED;
    }

    public void markFailed() {
        this.status = PaymentAttemptStatus.FAILED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public String getProviderPaymentId() {
        return providerPaymentId;
    }

    public PaymentAttemptStatus getStatus() {
        return status;
    }

    public int getAmountCents() {
        return amountCents;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
