package com.showtime.catalog.grpc;

import com.showtime.catalog.domain.SeatDefinition;
import com.showtime.catalog.domain.Show;
import com.showtime.catalog.repository.SeatDefinitionRepository;
import com.showtime.catalog.repository.ShowRepository;
import com.showtime.contracts.catalog.v1.CatalogQueryServiceGrpc;
import com.showtime.contracts.catalog.v1.GetSeatDefinitionsRequest;
import com.showtime.contracts.catalog.v1.GetSeatDefinitionsResponse;
import com.showtime.contracts.catalog.v1.GetShowRequest;
import com.showtime.contracts.catalog.v1.GetShowResponse;
import com.showtime.contracts.catalog.v1.SeatDefinition.Builder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.devh.boot.grpc.server.service.GrpcService;

/**
 * The only door Booking has into Catalog. Deliberately narrow (two read-only
 * RPCs) — Booking never gets a broader query surface than it needs, and
 * Catalog never accepts a write through this door.
 */
@GrpcService
public class CatalogGrpcService extends CatalogQueryServiceGrpc.CatalogQueryServiceImplBase {

    private final ShowRepository showRepository;
    private final SeatDefinitionRepository seatDefinitionRepository;

    public CatalogGrpcService(ShowRepository showRepository, SeatDefinitionRepository seatDefinitionRepository) {
        this.showRepository = showRepository;
        this.seatDefinitionRepository = seatDefinitionRepository;
    }

    @Override
    public void getShow(GetShowRequest request, StreamObserver<GetShowResponse> responseObserver) {
        UUID showId;
        try {
            showId = UUID.fromString(request.getShowId());
        } catch (IllegalArgumentException ex) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("malformed show_id").asRuntimeException());
            return;
        }

        Optional<Show> show = showRepository.findById(showId);
        if (show.isEmpty()) {
            responseObserver.onError(Status.NOT_FOUND.withDescription("show not found: " + showId).asRuntimeException());
            return;
        }

        responseObserver.onNext(toResponse(show.get()));
        responseObserver.onCompleted();
    }

    @Override
    public void getSeatDefinitions(
            GetSeatDefinitionsRequest request, StreamObserver<GetSeatDefinitionsResponse> responseObserver) {
        UUID showId;
        try {
            showId = UUID.fromString(request.getShowId());
        } catch (IllegalArgumentException ex) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("malformed show_id").asRuntimeException());
            return;
        }

        Optional<Show> show = showRepository.findById(showId);
        if (show.isEmpty()) {
            responseObserver.onError(Status.NOT_FOUND.withDescription("show not found: " + showId).asRuntimeException());
            return;
        }

        List<UUID> requestedSeatIds = request.getSeatIdsList().stream().map(UUID::fromString).toList();
        List<SeatDefinition> seats = seatDefinitionRepository.findByIdIn(requestedSeatIds);

        UUID screenId = show.get().getScreen().getId();
        boolean allBelongToScreen = seats.size() == requestedSeatIds.size()
                && seats.stream().allMatch(seat -> seat.getScreen().getId().equals(screenId));
        if (!allBelongToScreen) {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("one or more seat_ids do not belong to this show's screen")
                    .asRuntimeException());
            return;
        }

        GetSeatDefinitionsResponse.Builder response = GetSeatDefinitionsResponse.newBuilder();
        for (SeatDefinition seat : seats) {
            Builder seatBuilder = com.showtime.contracts.catalog.v1.SeatDefinition.newBuilder()
                    .setSeatId(seat.getId().toString())
                    .setSeatLabel(seat.getSeatLabel())
                    .setScreenId(seat.getScreen().getId().toString());
            response.addSeats(seatBuilder);
        }
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    private static GetShowResponse toResponse(Show show) {
        return GetShowResponse.newBuilder()
                .setShowId(show.getId().toString())
                .setMovieId(show.getMovie().getId().toString())
                .setScreenId(show.getScreen().getId().toString())
                .setStartsAtEpochMillis(show.getStartsAt().toEpochMilli())
                .setEndsAtEpochMillis(show.getEndsAt().toEpochMilli())
                .setTicketPriceCents(show.getTicketPriceCents())
                .build();
    }
}
