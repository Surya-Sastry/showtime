package com.showtime.catalog.repository;

import com.showtime.catalog.domain.SeatDefinition;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatDefinitionRepository extends JpaRepository<SeatDefinition, UUID> {

    List<SeatDefinition> findByScreenId(UUID screenId);

    List<SeatDefinition> findByIdIn(List<UUID> ids);
}
