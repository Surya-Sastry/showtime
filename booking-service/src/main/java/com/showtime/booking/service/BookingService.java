package com.showtime.booking.service;

import com.showtime.booking.domain.Booking;
import com.showtime.booking.domain.BookingStatus;
import com.showtime.booking.exception.BookingNotFoundException;
import com.showtime.booking.exception.BookingNotOwnedException;
import com.showtime.booking.payment.CreatePaymentResult;
import com.showtime.booking.payment.PaymentClient;
import com.showtime.booking.payment.PaymentUnavailableException;
import com.showtime.booking.repository.BookingRepository;
import com.showtime.booking.repository.PaymentAttemptRepository;
import com.showtime.booking.webhook.PaymentOutcome;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Public entry point for booking use cases. The actual DB transactions live
 * in {@code BookingTransactions} and {@code TransactionalWriteGuards}
 * (separate beans — see their Javadoc for why), so this class is free to
 * call an external HTTP payment call in between two transactional steps
 * without ever holding a database transaction open across that network
 * call, and free to sequence "record webhook receipt" and "apply its
 * effect" as two independent transactions.
 */
@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentClient paymentClient;
    private final BookingTransactions transactions;
    private final TransactionalWriteGuards writeGuards;

    public BookingService(
            BookingRepository bookingRepository,
            PaymentAttemptRepository paymentAttemptRepository,
            PaymentClient paymentClient,
            BookingTransactions transactions,
            TransactionalWriteGuards writeGuards) {
        this.bookingRepository = bookingRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.paymentClient = paymentClient;
        this.transactions = transactions;
        this.writeGuards = writeGuards;
    }

    public Booking createBooking(UUID userId, UUID holdId) {
        Booking booking = transactions.validateAndReserve(userId, holdId);
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            return booking; // idempotent replay after payment already resolved
        }
        if (!paymentAttemptRepository.findByBookingId(booking.getId()).isEmpty()) {
            return booking; // payment already initiated for this booking; don't double-initiate
        }

        try {
            CreatePaymentResult result = paymentClient.createPayment(booking.getId(), booking.getTotalPriceCents());
            transactions.recordPaymentAttempt(booking.getId(), result.providerPaymentId(), booking.getTotalPriceCents());
        } catch (PaymentUnavailableException ex) {
            transactions.compensateAfterPaymentInitiationFailure(booking.getId());
            throw ex;
        }
        return booking;
    }

    public void handlePaymentWebhook(String eventId, UUID bookingId, PaymentOutcome outcome) {
        try {
            // Recording the receipt runs in its own nested transaction (see
            // TransactionalWriteGuards). This method has no transaction of
            // its own, so by the time a duplicate-delivery exception
            // reaches this catch block, that nested transaction has
            // already cleanly rolled back — there's nothing left to poison.
            writeGuards.recordWebhookReceipt(eventId, bookingId);
        } catch (DataIntegrityViolationException ex) {
            return; // already processed by a previous (or concurrent) delivery
        }
        transactions.applyWebhookOutcome(bookingId, outcome);
    }

    public Booking getOwnedByUser(UUID userId, UUID bookingId) {
        Booking booking = bookingRepository.findByIdFetchingSeats(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        if (!booking.isOwnedBy(userId)) {
            throw new BookingNotOwnedException();
        }
        return booking;
    }

    public List<Booking> listForUser(UUID userId) {
        return bookingRepository.findByUserIdFetchingSeatsOrderByCreatedAtDesc(userId);
    }
}
