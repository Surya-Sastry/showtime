package com.showtime.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.showtime.booking.repository.ConfirmedSeatRepository;
import com.showtime.booking.support.FakeCatalogServer;
import com.showtime.booking.support.FakePaymentServer;
import com.showtime.booking.web.dto.CreateBookingRequest;
import com.showtime.booking.web.dto.CreateHoldRequest;
import com.showtime.booking.webhook.PaymentOutcome;
import com.showtime.booking.webhook.PaymentWebhookPayload;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end through the booking-service HTTP layer: hold seats, start
 * checkout, and resolve via a signed payment webhook — exercising the exact
 * paths described in the README's failure-scenario table (success, failure,
 * duplicate delivery). Catalog and the mock payment service are stood in
 * for by small real servers (see {@code support/}) rather than mocked at
 * the Java object level, so the real gRPC and HTTP client code runs.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class BookingFlowIntegrationTest {

    private static final String SIGNING_KEY = "integration-test-signing-key-32-bytes-minimum!!";
    private static final String WEBHOOK_SECRET = "integration-test-webhook-secret-32-bytes-min!!!";

    private static final FakeCatalogServer CATALOG_SERVER;
    private static final FakePaymentServer PAYMENT_SERVER;

    static {
        try {
            CATALOG_SERVER = new FakeCatalogServer();
            PAYMENT_SERVER = new FakePaymentServer();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("booking")
            .withUsername("booking")
            .withPassword("booking-test-password");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.url", () -> "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
        registry.add("showtime.jwt.signing-key", () -> SIGNING_KEY);
        registry.add("showtime.webhook.secret", () -> WEBHOOK_SECRET);
        registry.add("grpc.client.catalog-service.address", () -> "static://localhost:" + CATALOG_SERVER.port());
        registry.add("showtime.payment.base-url", () -> "http://localhost:" + PAYMENT_SERVER.port());
    }

    @AfterAll
    static void stopFakeServers() {
        CATALOG_SERVER.stop();
        PAYMENT_SERVER.stop();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ConfirmedSeatRepository confirmedSeatRepository;

    private static final int TICKET_PRICE_CENTS = 1200;

    private UUID showId;
    private Set<UUID> seatIds;
    private UUID userId;
    private String userToken;

    @BeforeEach
    void seedShowAndUser() {
        showId = UUID.randomUUID();
        seatIds = Set.of(UUID.randomUUID(), UUID.randomUUID());
        CATALOG_SERVER.registerShow(
                showId, UUID.randomUUID(), UUID.randomUUID(),
                Instant.now(), Instant.now().plusSeconds(7200), TICKET_PRICE_CENTS, seatIds);
        userId = UUID.randomUUID();
        userToken = token(userId, "CUSTOMER");
    }

    @Test
    void happyPathHoldThenBookingThenSuccessfulWebhookConfirmsTheBookingExactlyOnce() throws Exception {
        UUID holdId = createHold(seatIds);
        UUID bookingId = createBooking(holdId);

        mockMvc.perform(get("/bookings/" + bookingId).header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.totalPriceCents").value(TICKET_PRICE_CENTS * seatIds.size()));

        sendWebhook("evt-" + UUID.randomUUID(), bookingId, PaymentOutcome.SUCCEEDED);

        mockMvc.perform(get("/bookings/" + bookingId).header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.ticketCode").value(org.hamcrest.Matchers.startsWith("TKT-")));

        assertThat(confirmedSeatRepository.findAll().stream()
                .filter(seat -> seat.getBookingId().equals(bookingId))
                .count()).isEqualTo(seatIds.size());
    }

    @Test
    void aRedeliveredWebhookWithTheSameEventIdHasNoAdditionalEffect() throws Exception {
        UUID holdId = createHold(seatIds);
        UUID bookingId = createBooking(holdId);
        String eventId = "evt-" + UUID.randomUUID();

        sendWebhook(eventId, bookingId, PaymentOutcome.SUCCEEDED);
        sendWebhook(eventId, bookingId, PaymentOutcome.SUCCEEDED); // redelivered

        mockMvc.perform(get("/bookings/" + bookingId).header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        assertThat(confirmedSeatRepository.findAll().stream()
                .filter(seat -> seat.getBookingId().equals(bookingId))
                .count()).isEqualTo(seatIds.size());
    }

    @Test
    void aFailedPaymentWebhookCancelsTheBookingAndFreesTheSeatsForAnotherHold() throws Exception {
        UUID holdId = createHold(seatIds);
        UUID bookingId = createBooking(holdId);

        sendWebhook("evt-" + UUID.randomUUID(), bookingId, PaymentOutcome.FAILED);

        mockMvc.perform(get("/bookings/" + bookingId).header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // The Redis hold was released as compensation, so someone else can
        // now hold the exact same seats without waiting for the TTL.
        String anotherUserToken = token(UUID.randomUUID(), "CUSTOMER");
        mockMvc.perform(post("/shows/" + showId + "/holds")
                        .header("Authorization", "Bearer " + anotherUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateHoldRequest(seatIds))))
                .andExpect(status().isCreated());
    }

    @Test
    void anInvalidWebhookSignatureIsRejected() throws Exception {
        UUID holdId = createHold(seatIds);
        UUID bookingId = createBooking(holdId);
        String rawBody = objectMapper.writeValueAsString(
                new PaymentWebhookPayload("evt-bad-sig", bookingId, "pay-x", PaymentOutcome.SUCCEEDED));

        mockMvc.perform(post("/internal/webhooks/payments")
                        .header("X-Signature", "0000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void secondAttemptToHoldAnAlreadyHeldSeatIsRejected() throws Exception {
        createHold(seatIds);
        String otherUserToken = token(UUID.randomUUID(), "CUSTOMER");

        mockMvc.perform(post("/shows/" + showId + "/holds")
                        .header("Authorization", "Bearer " + otherUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateHoldRequest(seatIds))))
                .andExpect(status().isConflict());
    }

    @Test
    void aUserCannotReleaseAnotherUsersHold() throws Exception {
        UUID holdId = createHold(seatIds);
        String otherUserToken = token(UUID.randomUUID(), "CUSTOMER");

        mockMvc.perform(delete("/holds/" + holdId).header("Authorization", "Bearer " + otherUserToken))
                .andExpect(status().isForbidden());
    }

    private UUID createHold(Set<UUID> seats) throws Exception {
        String response = mockMvc.perform(post("/shows/" + showId + "/holds")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateHoldRequest(seats))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private UUID createBooking(UUID holdId) throws Exception {
        String response = mockMvc.perform(post("/bookings")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateBookingRequest(holdId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private void sendWebhook(String eventId, UUID bookingId, PaymentOutcome outcome) throws Exception {
        String rawBody = objectMapper.writeValueAsString(
                new PaymentWebhookPayload(eventId, bookingId, "pay-" + UUID.randomUUID(), outcome));
        String signature = sign(rawBody);

        mockMvc.perform(post("/internal/webhooks/payments")
                        .header("X-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBody))
                .andExpect(status().isOk());
    }

    private static String sign(String rawBody) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    private static String token(UUID subjectUserId, String role) {
        SecretKey key = Keys.hmacShaKeyFor(SIGNING_KEY.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subjectUserId.toString())
                .claim("role", role)
                .issuer("showtime-identity")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(900)))
                .signWith(key)
                .compact();
    }
}
