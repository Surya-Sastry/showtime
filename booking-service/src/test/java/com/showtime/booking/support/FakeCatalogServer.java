package com.showtime.booking.support;

import com.showtime.contracts.catalog.v1.CatalogQueryServiceGrpc;
import com.showtime.contracts.catalog.v1.GetSeatDefinitionsRequest;
import com.showtime.contracts.catalog.v1.GetSeatDefinitionsResponse;
import com.showtime.contracts.catalog.v1.GetShowRequest;
import com.showtime.contracts.catalog.v1.GetShowResponse;
import com.showtime.contracts.catalog.v1.SeatDefinition;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * A minimal, real gRPC server implementing exactly the CatalogQueryService
 * contract, used in place of the real Catalog service for booking-service
 * integration tests. It is a real server over a real (loopback) socket —
 * not a mock of the stub — so these tests exercise the actual gRPC client
 * code path in {@code CatalogClient}, including (de)serialization and
 * status-code translation.
 */
public class FakeCatalogServer {

    private final Server server;
    private final ConcurrentHashMap<String, GetShowResponse> shows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> seatsByShow = new ConcurrentHashMap<>();

    public FakeCatalogServer() throws IOException {
        this.server = ServerBuilder.forPort(0)
                .addService(new CatalogQueryServiceGrpc.CatalogQueryServiceImplBase() {
                    @Override
                    public void getShow(GetShowRequest request, StreamObserver<GetShowResponse> responseObserver) {
                        GetShowResponse response = shows.get(request.getShowId());
                        if (response == null) {
                            responseObserver.onError(Status.NOT_FOUND.asRuntimeException());
                            return;
                        }
                        responseObserver.onNext(response);
                        responseObserver.onCompleted();
                    }

                    @Override
                    public void getSeatDefinitions(
                            GetSeatDefinitionsRequest request, StreamObserver<GetSeatDefinitionsResponse> responseObserver) {
                        Set<String> validSeats = seatsByShow.getOrDefault(request.getShowId(), Set.of());
                        boolean allValid = request.getSeatIdsList().stream().allMatch(validSeats::contains);
                        if (!allValid) {
                            responseObserver.onError(Status.NOT_FOUND.asRuntimeException());
                            return;
                        }
                        GetSeatDefinitionsResponse.Builder response = GetSeatDefinitionsResponse.newBuilder();
                        request.getSeatIdsList().forEach(seatId -> response.addSeats(SeatDefinition.newBuilder()
                                .setSeatId(seatId)
                                .setSeatLabel("SEAT-" + seatId.substring(0, 4))
                                .setScreenId(request.getShowId())
                                .build()));
                        responseObserver.onNext(response.build());
                        responseObserver.onCompleted();
                    }
                })
                .build();
        this.server.start();
    }

    public int port() {
        return server.getPort();
    }

    public void registerShow(UUID showId, UUID movieId, UUID screenId, Instant startsAt, Instant endsAt, int ticketPriceCents, Set<UUID> seatIds) {
        shows.put(showId.toString(), GetShowResponse.newBuilder()
                .setShowId(showId.toString())
                .setMovieId(movieId.toString())
                .setScreenId(screenId.toString())
                .setStartsAtEpochMillis(startsAt.toEpochMilli())
                .setEndsAtEpochMillis(endsAt.toEpochMilli())
                .setTicketPriceCents(ticketPriceCents)
                .build());
        seatsByShow.put(showId.toString(), seatIds.stream().map(UUID::toString).collect(java.util.stream.Collectors.toSet()));
    }

    public void stop() {
        server.shutdownNow();
        try {
            server.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
