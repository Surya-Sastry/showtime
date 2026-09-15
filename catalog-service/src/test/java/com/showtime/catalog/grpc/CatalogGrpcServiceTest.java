package com.showtime.catalog.grpc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.showtime.catalog.domain.Movie;
import com.showtime.catalog.domain.Screen;
import com.showtime.catalog.domain.SeatDefinition;
import com.showtime.catalog.domain.Show;
import com.showtime.catalog.domain.Theater;
import com.showtime.catalog.repository.SeatDefinitionRepository;
import com.showtime.catalog.repository.ShowRepository;
import com.showtime.contracts.catalog.v1.GetSeatDefinitionsRequest;
import com.showtime.contracts.catalog.v1.GetSeatDefinitionsResponse;
import com.showtime.contracts.catalog.v1.GetShowRequest;
import com.showtime.contracts.catalog.v1.GetShowResponse;
import io.grpc.stub.StreamObserver;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CatalogGrpcServiceTest {

    private ShowRepository showRepository;
    private SeatDefinitionRepository seatDefinitionRepository;
    private CatalogGrpcService service;

    @BeforeEach
    void setUp() {
        showRepository = mock(ShowRepository.class);
        seatDefinitionRepository = mock(SeatDefinitionRepository.class);
        service = new CatalogGrpcService(showRepository, seatDefinitionRepository);
    }

    @Test
    void getShowReturnsNotFoundForAnUnknownShow() {
        UUID showId = UUID.randomUUID();
        Mockito.when(showRepository.findById(showId)).thenReturn(Optional.empty());

        @SuppressWarnings("unchecked")
        StreamObserver<GetShowResponse> observer = mock(StreamObserver.class);

        service.getShow(GetShowRequest.newBuilder().setShowId(showId.toString()).build(), observer);

        verify(observer, never()).onNext(any());
        verify(observer, times(1)).onError(any());
    }

    @Test
    void getShowReturnsShowDetailsForAKnownShow() throws Exception {
        Show show = buildShow();
        Mockito.when(showRepository.findById(show.getId())).thenReturn(Optional.of(show));

        @SuppressWarnings("unchecked")
        StreamObserver<GetShowResponse> observer = mock(StreamObserver.class);

        service.getShow(GetShowRequest.newBuilder().setShowId(show.getId().toString()).build(), observer);

        verify(observer).onNext(Mockito.argThat(response ->
                response.getShowId().equals(show.getId().toString())
                        && response.getTicketPriceCents() == show.getTicketPriceCents()));
        verify(observer).onCompleted();
    }

    @Test
    void getSeatDefinitionsRejectsASeatFromADifferentScreen() throws Exception {
        Show show = buildShow();
        Mockito.when(showRepository.findById(show.getId())).thenReturn(Optional.of(show));

        Screen otherScreen = new Screen(new Theater("Other Theater"), "Screen X");
        setId(otherScreen, UUID.randomUUID());
        SeatDefinition foreignSeat = new SeatDefinition(otherScreen, "Z9");
        UUID foreignSeatId = UUID.randomUUID();
        setId(foreignSeat, foreignSeatId);

        Mockito.when(seatDefinitionRepository.findByIdIn(List.of(foreignSeatId))).thenReturn(List.of(foreignSeat));

        @SuppressWarnings("unchecked")
        StreamObserver<GetSeatDefinitionsResponse> observer = mock(StreamObserver.class);

        service.getSeatDefinitions(
                GetSeatDefinitionsRequest.newBuilder()
                        .setShowId(show.getId().toString())
                        .addSeatIds(foreignSeatId.toString())
                        .build(),
                observer);

        verify(observer, never()).onNext(any());
        verify(observer, times(1)).onError(any());
    }

    private Show buildShow() throws Exception {
        Theater theater = new Theater("Downtown");
        setId(theater, UUID.randomUUID());
        Screen screen = new Screen(theater, "Screen 1");
        setId(screen, UUID.randomUUID());
        Movie movie = new Movie("A Test Movie", 120);
        setId(movie, UUID.randomUUID());
        Show show = new Show(movie, screen, Instant.now(), Instant.now().plusSeconds(7200), 1500);
        setId(show, UUID.randomUUID());
        return show;
    }

    private static void setId(Object entity, UUID id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }
}
