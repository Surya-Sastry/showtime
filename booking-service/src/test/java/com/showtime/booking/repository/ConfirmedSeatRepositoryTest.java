package com.showtime.booking.repository;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.showtime.booking.domain.Booking;
import com.showtime.booking.domain.ConfirmedSeat;
import com.showtime.booking.domain.SeatHold;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves the actual double-booking authority directly, independent of
 * Redis, gRPC, HTTP, or any application-level check: the database's
 * {@code UNIQUE(show_id, seat_id)} constraint on {@code booking_seats}. If
 * this constraint were ever accidentally dropped from a future migration,
 * this test — not a code review — is what would catch it.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ConfirmedSeatRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("booking")
            .withUsername("booking")
            .withPassword("booking-test-password");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private SeatHoldRepository seatHoldRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private ConfirmedSeatRepository confirmedSeatRepository;

    @Test
    void theSameSeatCannotBeConfirmedTwiceForTheSameShow() {
        UUID showId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        Booking bookingA = persistPendingBooking(showId, Set.of(seatId));
        Booking bookingB = persistPendingBooking(showId, Set.of(seatId));

        confirmedSeatRepository.saveAndFlush(new ConfirmedSeat(bookingA.getId(), showId, seatId, Instant.now()));

        assertThatThrownBy(() ->
                confirmedSeatRepository.saveAndFlush(new ConfirmedSeat(bookingB.getId(), showId, seatId, Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theSameSeatIdCanBeConfirmedForDifferentShows() {
        UUID seatId = UUID.randomUUID();
        UUID showA = UUID.randomUUID();
        UUID showB = UUID.randomUUID();
        Booking bookingA = persistPendingBooking(showA, Set.of(seatId));
        Booking bookingB = persistPendingBooking(showB, Set.of(seatId));

        confirmedSeatRepository.saveAndFlush(new ConfirmedSeat(bookingA.getId(), showA, seatId, Instant.now()));

        assertThatCode(() ->
                confirmedSeatRepository.saveAndFlush(new ConfirmedSeat(bookingB.getId(), showB, seatId, Instant.now())))
                .doesNotThrowAnyException();
    }

    private Booking persistPendingBooking(UUID showId, Set<UUID> seatIds) {
        Instant now = Instant.now();
        SeatHold hold = new SeatHold(UUID.randomUUID(), showId, UUID.randomUUID(), seatIds, now, now.plusSeconds(300));
        seatHoldRepository.saveAndFlush(hold);
        Booking booking = new Booking(hold.getUserId(), showId, hold.getId(), seatIds, 1000, now);
        return bookingRepository.saveAndFlush(booking);
    }
}
