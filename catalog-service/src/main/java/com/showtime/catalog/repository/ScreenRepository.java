package com.showtime.catalog.repository;

import com.showtime.catalog.domain.Screen;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScreenRepository extends JpaRepository<Screen, UUID> {

    // join fetch theater so callers can read the theater name without a
    // second round trip or a LazyInitializationException after the session
    // that loaded this screen has closed.
    @Query("select sc from Screen sc join fetch sc.theater where sc.id = :id")
    Optional<Screen> findWithTheaterById(@Param("id") UUID id);
}
