package com.showtime.catalog.repository;

import com.showtime.catalog.domain.Show;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShowRepository extends JpaRepository<Show, UUID> {

    // join fetch screen+theater so ShowResponse can read display names without
    // hitting a closed Hibernate session (open-in-view is disabled on purpose).
    @Query("select s from Show s join fetch s.screen sc join fetch sc.theater "
            + "where s.movie.id = :movieId and s.startsAt >= :dayStart and s.startsAt < :dayEnd order by s.startsAt")
    List<Show> findByMovieIdAndDate(
            @Param("movieId") UUID movieId,
            @Param("dayStart") Instant dayStart,
            @Param("dayEnd") Instant dayEnd);
}
