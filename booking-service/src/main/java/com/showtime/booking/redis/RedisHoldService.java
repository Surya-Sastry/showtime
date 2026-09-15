package com.showtime.booking.redis;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * The fast-path, TTL-backed coordination layer for seat holds.
 *
 * <p>Redis is NOT the source of truth for a confirmed booking (PostgreSQL's
 * {@code UNIQUE(show_id, seat_id)} constraint is) — it only answers "is
 * anyone else currently holding this seat" quickly enough to give users
 * responsive feedback, and it auto-expires so an abandoned hold doesn't
 * block a seat forever.
 *
 * <p>Both operations below are single Lua scripts so that acquiring or
 * releasing several seats at once is atomic: with a naive
 * check-then-set-per-seat approach, two concurrent requests for overlapping
 * seat sets could each pass the check for different seats and both partially
 * succeed. Redis executes an entire Lua script as one atomic step, so this
 * is impossible here — the script either reserves every requested seat or
 * none of them.
 */
@Component
public class RedisHoldService {

    private static final String HOLD_KEY_PREFIX = "hold:";

    // Returns 1 if every seat was free and is now held by this hold; 0 if
    // any seat was already held by someone else (and in that case NO seats
    // are touched — all-or-nothing).
    private static final String ACQUIRE_SCRIPT = """
            for i = 1, #KEYS do
              if redis.call('EXISTS', KEYS[i]) == 1 then
                return 0
              end
            end
            for i = 1, #KEYS do
              redis.call('SET', KEYS[i], ARGV[1], 'EX', ARGV[2])
            end
            return 1
            """;

    // Only deletes a seat key if it is still owned by this exact hold ID.
    // Without that check, releasing a hold whose TTL already lapsed could
    // delete a *different* hold's key if another request grabbed the seat
    // in the meantime.
    private static final String RELEASE_SCRIPT = """
            for i = 1, #KEYS do
              if redis.call('GET', KEYS[i]) == ARGV[1] then
                redis.call('DEL', KEYS[i])
              end
            end
            return 1
            """;

    private static final RedisScript<Long> ACQUIRE = new DefaultRedisScript<>(ACQUIRE_SCRIPT, Long.class);
    private static final RedisScript<Long> RELEASE = new DefaultRedisScript<>(RELEASE_SCRIPT, Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisHoldService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Attempts to atomically reserve every seat in {@code seatIds} for
     * {@code holdId}. Returns {@code true} only if all seats were free and
     * are now reserved; returns {@code false} (touching nothing) if any
     * single seat was already held.
     */
    public boolean acquire(UUID showId, Set<UUID> seatIds, UUID holdId, Duration ttl) {
        List<String> keys = seatKeys(showId, seatIds);
        Long result = redisTemplate.execute(ACQUIRE, keys, holdId.toString(), Long.toString(ttl.getSeconds()));
        return result != null && result == 1L;
    }

    /** Releases the given hold's seats, but only the keys it still owns. */
    public void release(UUID showId, Set<UUID> seatIds, UUID holdId) {
        List<String> keys = seatKeys(showId, seatIds);
        redisTemplate.execute(RELEASE, keys, holdId.toString());
    }

    private static List<String> seatKeys(UUID showId, Set<UUID> seatIds) {
        return seatIds.stream()
                .map(seatId -> HOLD_KEY_PREFIX + showId + ":" + seatId)
                .collect(Collectors.toList());
    }
}
