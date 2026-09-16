package com.showtime.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.showtime.gateway.support.FakeDownstreamServer;
import java.io.IOException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Proves two things the README calls out explicitly:
 *
 * <ol>
 *   <li>routing is correct even where paths overlap — Catalog owns
 *       {@code POST /shows} and {@code GET /shows/{id}/seats}, Booking owns
 *       {@code POST /shows/{id}/holds}, and a naive "/shows/**" predicate
 *       would send holds to the wrong service (this was an actual bug in
 *       the original route config, fixed alongside these tests);</li>
 *   <li>a correlation ID is generated when absent, preserved when present,
 *       forwarded to whichever service handles the request, and echoed
 *       back to the caller.</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingIntegrationTest {

    private static final FakeDownstreamServer IDENTITY;
    private static final FakeDownstreamServer CATALOG;
    private static final FakeDownstreamServer BOOKING;

    static {
        try {
            IDENTITY = new FakeDownstreamServer("identity");
            CATALOG = new FakeDownstreamServer("catalog");
            BOOKING = new FakeDownstreamServer("booking");
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("IDENTITY_SERVICE_URL", IDENTITY::baseUrl);
        registry.add("CATALOG_SERVICE_URL", CATALOG::baseUrl);
        registry.add("BOOKING_SERVICE_URL", BOOKING::baseUrl);
    }

    @AfterAll
    static void stopFakes() {
        IDENTITY.stop();
        CATALOG.stop();
        BOOKING.stop();
    }

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void routesAuthToIdentityWithTheApiPrefixStripped() {
        webTestClient.post().uri("/api/v1/auth/login").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.service").isEqualTo("identity")
                .jsonPath("$.path").isEqualTo("/auth/login");
    }

    @Test
    void routesMovieBrowsingToCatalog() {
        webTestClient.get().uri("/api/v1/movies").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.service").isEqualTo("catalog")
                .jsonPath("$.path").isEqualTo("/movies");
    }

    @Test
    void routesShowCreationToCatalogNotBooking() {
        webTestClient.post().uri("/api/v1/shows").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.service").isEqualTo("catalog")
                .jsonPath("$.path").isEqualTo("/shows");
    }

    @Test
    void routesSeatMapLookupToCatalog() {
        webTestClient.get().uri("/api/v1/shows/11111111-1111-1111-1111-111111111111/seats").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.service").isEqualTo("catalog")
                .jsonPath("$.path").isEqualTo("/shows/11111111-1111-1111-1111-111111111111/seats");
    }

    @Test
    void routesHoldCreationToBookingNotCatalogDespiteTheSharedShowsPrefix() {
        webTestClient.post().uri("/api/v1/shows/22222222-2222-2222-2222-222222222222/holds").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.service").isEqualTo("booking")
                .jsonPath("$.path").isEqualTo("/shows/22222222-2222-2222-2222-222222222222/holds");
    }

    @Test
    void routesBookingReadsToBooking() {
        webTestClient.get().uri("/api/v1/bookings").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.service").isEqualTo("booking")
                .jsonPath("$.path").isEqualTo("/bookings");
    }

    @Test
    void generatesACorrelationIdWhenTheCallerSendsNoneAndForwardsAndEchoesIt() {
        var response = webTestClient.get().uri("/api/v1/movies").exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Request-Id")
                .returnResult(Void.class);

        String returnedId = response.getResponseHeaders().getFirst("X-Request-Id");
        assertThat(returnedId).isNotBlank();

        var forwarded = CATALOG.received().get(CATALOG.received().size() - 1);
        assertThat(forwarded.correlationHeader()).isEqualTo(returnedId);
    }

    @Test
    void preservesACallerSuppliedCorrelationIdEndToEnd() {
        String callerSuppliedId = "caller-chosen-id-12345";

        var response = webTestClient.get().uri("/api/v1/movies")
                .header("X-Request-Id", callerSuppliedId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Request-Id", callerSuppliedId)
                .returnResult(Void.class);

        assertThat(response.getResponseHeaders().getFirst("X-Request-Id")).isEqualTo(callerSuppliedId);
        var forwarded = CATALOG.received().get(CATALOG.received().size() - 1);
        assertThat(forwarded.correlationHeader()).isEqualTo(callerSuppliedId);
    }
}
