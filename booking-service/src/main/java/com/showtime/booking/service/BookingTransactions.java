package com.showtime.booking.service;

import com.showtime.booking.catalog.CatalogClient;
import com.showtime.booking.catalog.ShowSnapshot;
import com.showtime.booking.domain.Booking;
import com.showtime.booking.domain.BookingStatus;
import com.showtime.booking.domain.PaymentAttempt;
import com.showtime.booking.domain.SeatHold;
import com.showtime.booking.exception.BookingNotFoundException;
import com.showtime.booking.exception.HoldExpiredException;
import com.showtime.booking.exception.HoldNotFoundException;
import com.showtime.booking.exception.HoldNotOwnedException;
import com.showtime.booking.redis.RedisHoldService;
import com.showtime.booking.repository.BookingRepository;
import com.showtime.booking.repository.PaymentAttemptRepository;
import com.showtime.booking.repository.SeatHoldRepository;
import com.showtime.booking.webhook.PaymentOutcome;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Holds every {@code @Transactional} step {@code BookingService} needs.
 *
 * <p>This is a separate bean on purpose. Spring's {@code @Transactional} is
 * implemented via a proxy wrapping the bean; calling an
 * {@code @Transactional} method via {@code this.method(...)} from within the
 * same class bypasses that proxy entirely (the well-known Spring AOP
 * "self-invocation" pitfall), silently running with no transaction at all.
 * Putting these methods on a different bean and calling them through
 * constructor-injected dependency guarantees the call goes through the
 * proxy every time. The same reasoning is why the actual risky inserts
 * live on {@code TransactionalWriteGuards}, yet another bean — see its
 * Javadoc.
 */
@Component
class BookingTransactions {

    private final SeatHoldRepository seatHoldRepository;
    private final BookingRepository bookingRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final CatalogClient catalogClient;
    private final RedisHoldService redisHoldService;
    private final TransactionalWriteGuards writeGuards;

    BookingTransactions(
            SeatHoldRepository seatHoldRepository,
            BookingRepository bookingRepository,
            PaymentAttemptRepository paymentAttemptRepository,
            CatalogClient catalogClient,
            RedisHoldService redisHoldService,
            TransactionalWriteGuards writeGuards) {
        this.seatHoldRepository = seatHoldRepository;
        this.bookingRepository = bookingRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.catalogClient = catalogClient;
        this.redisHoldService = redisHoldService;
        this.writeGuards = writeGuards;
    }

    @Transactional
    Booking validateAndReserve(UUID userId, UUID holdId) {
        var existing = bookingRepository.findByHoldIdFetchingSeats(holdId);
        if (existing.isPresent()) {
            return existing.get();
        }

        SeatHold hold = seatHoldRepository.findById(holdId).orElseThrow(() -> new HoldNotFoundException(holdId));
        if (!hold.isOwnedBy(userId)) {
            throw new HoldNotOwnedException();
        }
        Instant now = Instant.now();
        if (!hold.isActive(now)) {
            throw new HoldExpiredException();
        }

        // Price always comes from Catalog, never from the client.
        ShowSnapshot show = catalogClient.getShow(hold.getShowId());
        int totalPriceCents = show.ticketPriceCents() * hold.getSeatIds().size();

        Booking booking = new Booking(userId, hold.getShowId(), holdId, hold.getSeatIds(), totalPriceCents, now);
        hold.markConsumed();
        seatHoldRepository.save(hold);

        try {
            // Runs in its own nested transaction (see
            // TransactionalWriteGuards); if it throws, that nested
            // transaction has already fully rolled back by the time we
            // catch it here, so this method's own transaction is completely
            // unaffected and safe to keep using below.
            return writeGuards.insertBooking(booking);
        } catch (DataIntegrityViolationException ex) {
            // Lost the UNIQUE(hold_id) race to a concurrent call for this
            // same hold — return the winner's booking instead of failing.
            return bookingRepository.findByHoldIdFetchingSeats(holdId).orElseThrow(() -> ex);
        }
    }

    @Transactional
    void recordPaymentAttempt(UUID bookingId, String providerPaymentId, int amountCents) {
        paymentAttemptRepository.save(new PaymentAttempt(bookingId, providerPaymentId, amountCents, Instant.now()));
    }

    @Transactional
    void compensateAfterPaymentInitiationFailure(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow(() -> new BookingNotFoundException(bookingId));
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            return;
        }
        booking.transitionTo(BookingStatus.CANCELLED, Instant.now());
        seatHoldRepository.findById(booking.getHoldId()).ifPresent(hold -> {
            redisHoldService.release(hold.getShowId(), hold.getSeatIds(), hold.getId());
            hold.markReleased();
            seatHoldRepository.save(hold);
        });
    }

    @Transactional
    void applyWebhookOutcome(UUID bookingId, PaymentOutcome outcome) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow(() -> new BookingNotFoundException(bookingId));
        if (booking.getStatus().isTerminal()) {
            return; // already resolved; a webhook must never repeat its effect
        }

        if (outcome == PaymentOutcome.SUCCEEDED) {
            confirmBooking(booking);
        } else {
            cancelBookingAndReleaseHold(booking);
        }
    }

    private void confirmBooking(Booking booking) {
        Instant now = Instant.now();
        try {
            // Runs in its own nested transaction; if it throws, that
            // nested transaction has already fully rolled back by the time
            // we catch it here, so applyWebhookOutcome's own transaction
            // (still open, still clean) is unaffected.
            writeGuards.confirmSeats(booking.getId(), booking.getShowId(), booking.getRequestedSeatIds(), now);
            booking.confirmWithTicketCode(generateTicketCode(booking.getId()), now);
        } catch (DataIntegrityViolationException ex) {
            // Payment succeeded but the hold had already expired and
            // someone else's booking confirmed at least one of these seats
            // first. Money moved, seats didn't — flag for manual
            // reconciliation/refund instead of silently double-booking or
            // silently eating the customer's payment.
            booking.transitionTo(BookingStatus.REQUIRES_REVIEW, now);
        }
    }

    private void cancelBookingAndReleaseHold(Booking booking) {
        Instant now = Instant.now();
        booking.transitionTo(BookingStatus.CANCELLED, now);
        seatHoldRepository.findById(booking.getHoldId()).ifPresent(hold -> {
            redisHoldService.release(hold.getShowId(), hold.getSeatIds(), hold.getId());
            hold.markReleased();
        });
    }

    private static String generateTicketCode(UUID bookingId) {
        return "TKT-" + bookingId.toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }
}
