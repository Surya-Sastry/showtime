package com.showtime.payment.repository;

import com.showtime.payment.domain.SimulatedPayment;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** A thread-safe in-memory store — see {@code SimulatedPayment}'s Javadoc for why there's no database here. */
@Component
public class PaymentStore {

    private final Map<String, SimulatedPayment> paymentsById = new ConcurrentHashMap<>();

    public SimulatedPayment save(SimulatedPayment payment) {
        paymentsById.put(payment.getPaymentId(), payment);
        return payment;
    }

    public Optional<SimulatedPayment> findById(String paymentId) {
        return Optional.ofNullable(paymentsById.get(paymentId));
    }
}
