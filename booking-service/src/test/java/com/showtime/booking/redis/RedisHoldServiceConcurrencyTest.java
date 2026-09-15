package com.showtime.booking.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * This is the test the README calls out explicitly: "Launch many concurrent
 * attempts for one seat; exactly one confirms." It runs against a real
 * Redis (Testcontainers), not a mock, because the entire point of
 * {@code RedisHoldService} is a race-condition guarantee that only a real
 * single-threaded Lua execution can provide — a mocked Redis client would
 * not be able to falsify a broken implementation the way a real one can.
 */
@Testcontainers
class RedisHoldServiceConcurrencyTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static RedisHoldService redisHoldService;

    @BeforeAll
    static void startRedisClient() {
        connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
        redisHoldService = new RedisHoldService(template);
    }

    @AfterAll
    static void stopRedisClient() {
        connectionFactory.destroy();
    }

    @Test
    void exactlyOneOfManyConcurrentAttemptsForTheSameSingleSeatSucceeds() throws Exception {
        UUID showId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        int attempts = 50;

        List<Boolean> results = runConcurrently(attempts, () ->
                redisHoldService.acquire(showId, Set.of(seatId), UUID.randomUUID(), Duration.ofSeconds(60)));

        long successCount = results.stream().filter(Boolean::booleanValue).count();
        assertThat(successCount).isEqualTo(1);
    }

    @Test
    void concurrentAttemptsForOverlappingMultiSeatSetsLeaveEachSeatWithExactlyOneOwner() throws Exception {
        UUID showId = UUID.randomUUID();
        UUID seatA = UUID.randomUUID();
        UUID seatB = UUID.randomUUID();
        UUID seatC = UUID.randomUUID();
        // Three overlapping requests: {A,B}, {B,C}, {A,C}. At most one can
        // win, because whichever succeeds first locks two of the three
        // seats and the other two both need at least one already-taken seat.
        List<Set<UUID>> requests = List.of(Set.of(seatA, seatB), Set.of(seatB, seatC), Set.of(seatA, seatC));

        List<Boolean> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(3)) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Boolean>> futures = new ArrayList<>();
            for (Set<UUID> seats : requests) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return redisHoldService.acquire(showId, seats, UUID.randomUUID(), Duration.ofSeconds(60));
                }));
            }
            start.countDown();
            for (Future<Boolean> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }
        }

        long successCount = results.stream().filter(Boolean::booleanValue).count();
        assertThat(successCount).isEqualTo(1);
    }

    @Test
    void acquireIsAllOrNothingWhenOneSeatIsAlreadyTaken() {
        UUID showId = UUID.randomUUID();
        UUID freeSeat = UUID.randomUUID();
        UUID takenSeat = UUID.randomUUID();
        UUID firstHolder = UUID.randomUUID();
        assertThat(redisHoldService.acquire(showId, Set.of(takenSeat), firstHolder, Duration.ofSeconds(60))).isTrue();

        UUID secondHolder = UUID.randomUUID();
        boolean acquired = redisHoldService.acquire(showId, Set.of(freeSeat, takenSeat), secondHolder, Duration.ofSeconds(60));

        assertThat(acquired).isFalse();
        // "All or nothing": the free seat must NOT have been reserved by
        // the failed attempt either.
        assertThat(redisHoldService.acquire(showId, Set.of(freeSeat), UUID.randomUUID(), Duration.ofSeconds(60))).isTrue();
    }

    @Test
    void releaseOnlyFreesSeatsStillOwnedByThatExactHold() {
        UUID showId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        UUID holdA = UUID.randomUUID();
        UUID holdB = UUID.randomUUID();
        assertThat(redisHoldService.acquire(showId, Set.of(seatId), holdA, Duration.ofSeconds(60))).isTrue();

        // holdB never owned this seat; releasing with holdB's id must be a no-op.
        redisHoldService.release(showId, Set.of(seatId), holdB);
        assertThat(redisHoldService.acquire(showId, Set.of(seatId), UUID.randomUUID(), Duration.ofSeconds(60))).isFalse();

        // The real owner can release it, freeing it for someone else.
        redisHoldService.release(showId, Set.of(seatId), holdA);
        assertThat(redisHoldService.acquire(showId, Set.of(seatId), UUID.randomUUID(), Duration.ofSeconds(60))).isTrue();
    }

    private static List<Boolean> runConcurrently(int count, java.util.concurrent.Callable<Boolean> task) throws Exception {
        List<Boolean> results = new ArrayList<>();
        AtomicInteger errors = new AtomicInteger();
        try (ExecutorService pool = Executors.newFixedThreadPool(Math.min(count, 32))) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            for (Future<Boolean> future : futures) {
                try {
                    results.add(future.get(10, TimeUnit.SECONDS));
                } catch (Exception ex) {
                    errors.incrementAndGet();
                }
            }
        }
        assertThat(errors.get()).as("no acquire attempt should throw").isZero();
        return results;
    }
}
