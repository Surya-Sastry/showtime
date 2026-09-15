package com.showtime.booking.repository;

import com.showtime.booking.domain.Booking;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    Optional<Booking> findByHoldId(UUID holdId);

    List<Booking> findByUserIdOrderByCreatedAtDesc(UUID userId);

    // These *FetchingSeats variants join-fetch the requestedSeatIds element
    // collection (LAZY by default) and must be used for any Booking that
    // will be read after this method's transaction closes — e.g. to build
    // an HTTP response — since open-in-view is disabled and an
    // uninitialized lazy collection can't be touched once the session is
    // gone.
    @Query("select distinct b from Booking b left join fetch b.requestedSeatIds where b.id = :id")
    Optional<Booking> findByIdFetchingSeats(@Param("id") UUID id);

    @Query("select distinct b from Booking b left join fetch b.requestedSeatIds where b.holdId = :holdId")
    Optional<Booking> findByHoldIdFetchingSeats(@Param("holdId") UUID holdId);

    @Query("select distinct b from Booking b left join fetch b.requestedSeatIds where b.userId = :userId order by b.createdAt desc")
    List<Booking> findByUserIdFetchingSeatsOrderByCreatedAtDesc(@Param("userId") UUID userId);
}
