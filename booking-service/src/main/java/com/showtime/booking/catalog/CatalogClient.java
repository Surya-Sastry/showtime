package com.showtime.booking.catalog;

import com.showtime.contracts.catalog.v1.CatalogQueryServiceGrpc;
import com.showtime.contracts.catalog.v1.GetSeatDefinitionsRequest;
import com.showtime.contracts.catalog.v1.GetSeatDefinitionsResponse;
import com.showtime.contracts.catalog.v1.GetShowRequest;
import com.showtime.contracts.catalog.v1.GetShowResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

/**
 * Booking's only door into Catalog. Every call carries an explicit deadline
 * (no unbounded waits) and is wrapped with a circuit breaker; GetShow/
 * GetSeatDefinitions are pure reads, so bounded retries are also safe here
 * per the README's resilience guidance ("retries happen only for
 * safe/transient operations").
 */
@Component
public class CatalogClient {

    private static final long DEADLINE_SECONDS = 2;

    @GrpcClient("catalog-service")
    private CatalogQueryServiceGrpc.CatalogQueryServiceBlockingStub catalogStub;

    @CircuitBreaker(name = "catalog")
    @Retry(name = "catalog")
    public ShowSnapshot getShow(UUID showId) {
        try {
            GetShowResponse response = catalogStub
                    .withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .getShow(GetShowRequest.newBuilder().setShowId(showId.toString()).build());
            return new ShowSnapshot(
                    UUID.fromString(response.getShowId()),
                    UUID.fromString(response.getMovieId()),
                    UUID.fromString(response.getScreenId()),
                    Instant.ofEpochMilli(response.getStartsAtEpochMillis()),
                    Instant.ofEpochMilli(response.getEndsAtEpochMillis()),
                    response.getTicketPriceCents());
        } catch (StatusRuntimeException ex) {
            throw translate(ex, showId);
        }
    }

    @CircuitBreaker(name = "catalog")
    @Retry(name = "catalog")
    public void validateSeatsBelongToShow(UUID showId, Set<UUID> seatIds) {
        try {
            GetSeatDefinitionsRequest.Builder request = GetSeatDefinitionsRequest.newBuilder().setShowId(showId.toString());
            seatIds.forEach(seatId -> request.addSeatIds(seatId.toString()));

            GetSeatDefinitionsResponse response = catalogStub
                    .withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .getSeatDefinitions(request.build());

            if (response.getSeatsCount() != seatIds.size()) {
                throw new InvalidSeatSelectionException(
                        "one or more requested seats do not belong to show " + showId);
            }
        } catch (StatusRuntimeException ex) {
            throw translate(ex, showId);
        }
    }

    private RuntimeException translate(StatusRuntimeException ex, UUID showId) {
        if (ex.getStatus().getCode() == Status.Code.NOT_FOUND) {
            return new ShowNotFoundException(showId);
        }
        if (ex.getStatus().getCode() == Status.Code.INVALID_ARGUMENT) {
            return new InvalidSeatSelectionException(ex.getStatus().getDescription());
        }
        // DEADLINE_EXCEEDED, UNAVAILABLE, or anything else: a controlled
        // dependency error, not a hang or an uncaught 500.
        return new CatalogUnavailableException("catalog call failed: " + ex.getStatus(), ex);
    }
}
