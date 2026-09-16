package com.showtime.payment.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record CreatePaymentRequest(@NotNull UUID bookingId, @Positive int amountCents) {
}
