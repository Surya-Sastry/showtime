package com.showtime.booking.service;

import com.showtime.booking.domain.Booking;
import com.showtime.booking.domain.ConfirmedSeat;
import com.showtime.booking.domain.WebhookReceipt;
import com.showtime.booking.repository.BookingRepository;
import com.showtime.booking.repository.ConfirmedSeatRepository;
import com.showtime.booking.repository.WebhookReceiptRepository;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every write here can fail on a UNIQUE constraint we expect to sometimes
 * lose a race against (hold_id, show_id+seat_id, event_id) — that's the
 * whole point of those constraints. Each method runs in its own
 * {@code REQUIRES_NEW} transaction, and deliberately lets
 * {@code DataIntegrityViolationException} propagate rather than catching it
 * here.
 *
 * <p>This split matters for a subtle reason: per the JPA specification,
 * once {@code flush()} throws, the CURRENT transaction is required to be
 * marked rollback-only — this happens regardless of whether the Java
 * exception is caught, and regardless of {@code REQUIRES_NEW}. So catching
 * the exception inside the same {@code @Transactional} method that called
 * flush and then returning normally does NOT help: Spring would still
 * detect the rollback-only transaction at method exit and throw
 * {@code UnexpectedRollbackException} instead of committing.
 *
 * <p>The fix is to let the exception propagate out of THIS bean's proxy.
 * Spring's transaction interceptor then rolls back this nested
 * {@code REQUIRES_NEW} transaction as part of normal exception handling
 * (rollback completes before the exception is rethrown), and only then
 * does the exception reach the caller — a different bean, on its own,
 * completely unaffected transaction — where it is safe to catch it and
 * decide what "this was actually a duplicate/conflict" means.
 */
@Component
class TransactionalWriteGuards {

    private final BookingRepository bookingRepository;
    private final ConfirmedSeatRepository confirmedSeatRepository;
    private final WebhookReceiptRepository webhookReceiptRepository;

    TransactionalWriteGuards(
            BookingRepository bookingRepository,
            ConfirmedSeatRepository confirmedSeatRepository,
            WebhookReceiptRepository webhookReceiptRepository) {
        this.bookingRepository = bookingRepository;
        this.confirmedSeatRepository = confirmedSeatRepository;
        this.webhookReceiptRepository = webhookReceiptRepository;
    }

    /** @throws DataIntegrityViolationException if UNIQUE(hold_id) rejects this as a duplicate. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Booking insertBooking(Booking booking) {
        return bookingRepository.saveAndFlush(booking);
    }

    /** @throws DataIntegrityViolationException if UNIQUE(show_id, seat_id) rejects any seat as already confirmed. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void confirmSeats(UUID bookingId, UUID showId, Set<UUID> seatIds, Instant confirmedAt) {
        for (UUID seatId : seatIds) {
            confirmedSeatRepository.save(new ConfirmedSeat(bookingId, showId, seatId, confirmedAt));
        }
        confirmedSeatRepository.flush();
    }

    /** @throws DataIntegrityViolationException if UNIQUE(event_id) shows this delivery was already processed. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void recordWebhookReceipt(String eventId, UUID bookingId) {
        webhookReceiptRepository.saveAndFlush(new WebhookReceipt(eventId, bookingId, Instant.now()));
    }
}
