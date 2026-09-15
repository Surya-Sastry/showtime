package com.showtime.booking.repository;

import com.showtime.booking.domain.SeatHold;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatHoldRepository extends JpaRepository<SeatHold, UUID> {
}
