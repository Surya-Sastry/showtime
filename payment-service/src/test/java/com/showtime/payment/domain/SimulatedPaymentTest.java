package com.showtime.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class SimulatedPaymentTest {

    @Test
    void startsInCreatedStatus() {
        SimulatedPayment payment = new SimulatedPayment(UUID.randomUUID(), 1200);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(payment.getPaymentId()).startsWith("pay-");
    }

    @Test
    void firstCompletionSucceedsAndChangesStatus() {
        SimulatedPayment payment = new SimulatedPayment(UUID.randomUUID(), 1200);

        boolean transitioned = payment.complete(PaymentStatus.SUCCEEDED);

        assertThat(transitioned).isTrue();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void completingTwiceWithTheSameOutcomeReportsNoFurtherTransition() {
        SimulatedPayment payment = new SimulatedPayment(UUID.randomUUID(), 1200);
        payment.complete(PaymentStatus.SUCCEEDED);

        boolean transitionedAgain = payment.complete(PaymentStatus.SUCCEEDED);

        assertThat(transitionedAgain).isFalse();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED); // unchanged, not flipped
    }

    @Test
    void cannotCompleteBackIntoCreated() {
        SimulatedPayment payment = new SimulatedPayment(UUID.randomUUID(), 1200);

        assertThatThrownBy(() -> payment.complete(PaymentStatus.CREATED)).isInstanceOf(IllegalArgumentException.class);
    }
}
