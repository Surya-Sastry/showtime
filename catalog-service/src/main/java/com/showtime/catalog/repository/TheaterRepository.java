package com.showtime.catalog.repository;

import com.showtime.catalog.domain.Theater;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TheaterRepository extends JpaRepository<Theater, UUID> {
}
