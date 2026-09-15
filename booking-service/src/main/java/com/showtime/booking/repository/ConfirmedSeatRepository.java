package com.showtime.booking.repository;

import com.showtime.booking.domain.ConfirmedSeat;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfirmedSeatRepository extends JpaRepository<ConfirmedSeat, UUID> {
}
