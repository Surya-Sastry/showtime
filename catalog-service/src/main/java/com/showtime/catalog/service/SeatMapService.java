package com.showtime.catalog.service;

import com.showtime.catalog.domain.SeatDefinition;
import com.showtime.catalog.domain.Show;
import com.showtime.catalog.repository.SeatDefinitionRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Resolves the seat map for a show. Catalog does not know which seats are
 * held or booked — that lives entirely in Booking. This only answers
 * "what seats exist on this show's screen," which is exactly what Booking's
 * gRPC call needs to validate a hold request.
 */
@Service
public class SeatMapService {

    private final SeatDefinitionRepository seatDefinitionRepository;

    public SeatMapService(SeatDefinitionRepository seatDefinitionRepository) {
        this.seatDefinitionRepository = seatDefinitionRepository;
    }

    public List<SeatDefinition> seatsFor(Show show) {
        return seatDefinitionRepository.findByScreenId(show.getScreen().getId());
    }
}
