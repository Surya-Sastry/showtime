package com.showtime.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BookingStatusTest {

    @Test
    void pendingPaymentCanReachEveryTerminalOutcome() {
        assertThat(BookingStatus.PENDING_PAYMENT.canTransitionTo(BookingStatus.CONFIRMED)).isTrue();
        assertThat(BookingStatus.PENDING_PAYMENT.canTransitionTo(BookingStatus.CANCELLED)).isTrue();
        assertThat(BookingStatus.PENDING_PAYMENT.canTransitionTo(BookingStatus.EXPIRED)).isTrue();
        assertThat(BookingStatus.PENDING_PAYMENT.canTransitionTo(BookingStatus.REQUIRES_REVIEW)).isTrue();
    }

    @Test
    void pendingPaymentCannotTransitionToItself() {
        assertThat(BookingStatus.PENDING_PAYMENT.canTransitionTo(BookingStatus.PENDING_PAYMENT)).isFalse();
    }

    @Test
    void terminalStatusesAcceptNoFurtherTransitions() {
        for (BookingStatus terminal : new BookingStatus[] {
                BookingStatus.CONFIRMED, BookingStatus.CANCELLED, BookingStatus.EXPIRED, BookingStatus.REQUIRES_REVIEW}) {
            assertThat(terminal.isTerminal()).isTrue();
            for (BookingStatus candidate : BookingStatus.values()) {
                assertThat(terminal.canTransitionTo(candidate))
                        .as("%s -> %s should be rejected", terminal, candidate)
                        .isFalse();
            }
        }
    }

    @Test
    void pendingPaymentIsNotTerminal() {
        assertThat(BookingStatus.PENDING_PAYMENT.isTerminal()).isFalse();
    }
}
