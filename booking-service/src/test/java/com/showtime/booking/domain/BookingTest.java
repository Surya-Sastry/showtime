package com.showtime.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BookingTest {

    @Test
    void startsInPendingPaymentWithAFreshIdempotencyKey() {
        Instant now = Instant.now();
        Booking booking = new Booking(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Set.of(UUID.randomUUID()), 1500, now);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(booking.getIdempotencyKey()).isNotNull();
        assertThat(booking.getTicketCode()).isNull();
    }

    @Test
    void confirmingSetsStatusAndTicketCodeTogether() {
        Instant now = Instant.now();
        Booking booking = new Booking(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Set.of(UUID.randomUUID()), 1500, now);

        booking.confirmWithTicketCode("TKT-ABC12345", now.plusSeconds(5));

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getTicketCode()).isEqualTo("TKT-ABC12345");
    }

    @Test
    void cannotConfirmACancelledBooking() {
        Instant now = Instant.now();
        Booking booking = new Booking(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Set.of(UUID.randomUUID()), 1500, now);
        booking.transitionTo(BookingStatus.CANCELLED, now);

        assertThatThrownBy(() -> booking.confirmWithTicketCode("TKT-X", now)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsANonPositiveTotalPrice() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> new Booking(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Set.of(UUID.randomUUID()), 0, now))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
