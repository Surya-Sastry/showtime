package com.showtime.catalog.repository;

import com.showtime.catalog.domain.Movie;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovieRepository extends JpaRepository<Movie, UUID> {

    Page<Movie> findAllBy(Pageable pageable);
}
