package com.showtime.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SeatHoldTest {

    private final UUID holdId = UUID.randomUUID();
    private final UUID showId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final Set<UUID> seatIds = Set.of(UUID.randomUUID());

    @Test
    void aHoldRequiresAtLeastOneSeat() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> new SeatHold(holdId, showId, userId, Set.of(), now, now.plusSeconds(300)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isActiveIsFalseOncePastExpiry() {
        Instant now = Instant.now();
        SeatHold hold = new SeatHold(holdId, showId, userId, seatIds, now.minusSeconds(400), now.minusSeconds(100));

        assertThat(hold.isActive(now)).isFalse();
    }

    @Test
    void isActiveIsTrueBeforeExpiry() {
        Instant now = Instant.now();
        SeatHold hold = new SeatHold(holdId, showId, userId, seatIds, now, now.plusSeconds(300));

        assertThat(hold.isActive(now)).isTrue();
    }

    @Test
    void onlyAnActiveHoldCanBeConsumed() {
        Instant now = Instant.now();
        SeatHold hold = new SeatHold(holdId, showId, userId, seatIds, now, now.plusSeconds(300));
        hold.markConsumed();

        assertThatThrownBy(hold::markConsumed).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void releasingAConsumedHoldIsAllowedAsCompensation() {
        Instant now = Instant.now();
        SeatHold hold = new SeatHold(holdId, showId, userId, seatIds, now, now.plusSeconds(300));
        hold.markConsumed();

        hold.markReleased();

        assertThat(hold.getStatus()).isEqualTo(HoldStatus.RELEASED);
    }

    @Test
    void ownershipCheckOnlyMatchesTheOriginalUser() {
        Instant now = Instant.now();
        SeatHold hold = new SeatHold(holdId, showId, userId, seatIds, now, now.plusSeconds(300));

        assertThat(hold.isOwnedBy(userId)).isTrue();
        assertThat(hold.isOwnedBy(UUID.randomUUID())).isFalse();
    }
}
