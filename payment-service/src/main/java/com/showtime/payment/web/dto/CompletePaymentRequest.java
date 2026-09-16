package com.showtime.payment.web.dto;

import com.showtime.payment.domain.PaymentOutcome;
import jakarta.validation.constraints.NotNull;

public record CompletePaymentRequest(@NotNull PaymentOutcome outcome) {
}
