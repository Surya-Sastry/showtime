package com.showtime.payment.web;

import com.showtime.payment.domain.SimulatedPayment;
import com.showtime.payment.service.PaymentService;
import com.showtime.payment.web.dto.CompletePaymentRequest;
import com.showtime.payment.web.dto.CreatePaymentRequest;
import com.showtime.payment.web.dto.PaymentResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal-only endpoints (no JWT auth: callers are other services and, for
 * {@code /complete}, test/ops tooling simulating a payment provider's
 * decision — see the README's "Mock payment REST/webhook" API outline).
 */
@RestController
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/internal/payments")
    public ResponseEntity<PaymentResponse> createPayment(@Valid @RequestBody CreatePaymentRequest request) {
        SimulatedPayment payment = paymentService.createPayment(request.bookingId(), request.amountCents());
        return ResponseEntity.status(HttpStatus.CREATED).body(PaymentResponse.from(payment));
    }

    @PostMapping("/internal/payments/{paymentId}/complete")
    public PaymentResponse completePayment(
            @PathVariable("paymentId") String paymentId, @Valid @RequestBody CompletePaymentRequest request) {
        SimulatedPayment payment = paymentService.complete(paymentId, request.outcome());
        return PaymentResponse.from(payment);
    }

    @GetMapping("/internal/payments/{paymentId}")
    public PaymentResponse getPayment(@PathVariable("paymentId") String paymentId) {
        return PaymentResponse.from(paymentService.getPayment(paymentId));
    }
}
