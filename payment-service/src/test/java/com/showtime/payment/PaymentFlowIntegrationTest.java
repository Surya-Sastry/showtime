package com.showtime.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.showtime.payment.support.FakeBookingWebhookServer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exercises the full simulated-payment flow against a real (loopback) fake
 * of Booking's webhook endpoint, verifying: a settled payment delivers
 * exactly one correctly-signed webhook; re-completing with the same
 * outcome redelivers the SAME event ID (the "duplicate callback" scenario
 * from the README); and a conflicting re-completion is rejected.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentFlowIntegrationTest {

    private static final String WEBHOOK_SECRET = "payment-integration-test-secret-32-bytes-min!!";

    private static final FakeBookingWebhookServer BOOKING_SERVER;

    static {
        try {
            BOOKING_SERVER = new FakeBookingWebhookServer();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("showtime.webhook.secret", () -> WEBHOOK_SECRET);
        registry.add("showtime.webhook.booking-callback-url", BOOKING_SERVER::url);
    }

    @AfterAll
    static void stopFakeServer() {
        BOOKING_SERVER.stop();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void completingAPaymentDeliversExactlyOneCorrectlySignedWebhook() throws Exception {
        UUID bookingId = UUID.randomUUID();
        String paymentId = createPayment(bookingId, 1500);

        mockMvc.perform(post("/internal/payments/" + paymentId + "/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"SUCCEEDED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        var deliveriesForThisPayment = deliveriesFor(paymentId);
        assertThat(deliveriesForThisPayment).hasSize(1);
        var delivery = deliveriesForThisPayment.get(0);
        assertThat(delivery.signature()).isEqualTo(sign(delivery.body()));

        JsonNode payload = objectMapper.readTree(delivery.body());
        assertThat(payload.get("bookingId").asText()).isEqualTo(bookingId.toString());
        assertThat(payload.get("providerPaymentId").asText()).isEqualTo(paymentId);
        assertThat(payload.get("outcome").asText()).isEqualTo("SUCCEEDED");
    }

    @Test
    void completingTheSamePaymentTwiceRedeliversTheIdenticalEventId() throws Exception {
        UUID bookingId = UUID.randomUUID();
        String paymentId = createPayment(bookingId, 1500);

        mockMvc.perform(post("/internal/payments/" + paymentId + "/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"SUCCEEDED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/internal/payments/" + paymentId + "/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"SUCCEEDED\"}"))
                .andExpect(status().isOk());

        var deliveriesForThisPayment = deliveriesFor(paymentId);
        assertThat(deliveriesForThisPayment).hasSize(2);
        String firstEventId = objectMapper.readTree(deliveriesForThisPayment.get(0).body()).get("eventId").asText();
        String secondEventId = objectMapper.readTree(deliveriesForThisPayment.get(1).body()).get("eventId").asText();
        assertThat(secondEventId).isEqualTo(firstEventId);
    }

    @Test
    void completingWithAConflictingOutcomeIsRejected() throws Exception {
        UUID bookingId = UUID.randomUUID();
        String paymentId = createPayment(bookingId, 1500);

        mockMvc.perform(post("/internal/payments/" + paymentId + "/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"SUCCEEDED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/internal/payments/" + paymentId + "/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"FAILED\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void completingAnUnknownPaymentIsNotFound() throws Exception {
        mockMvc.perform(post("/internal/payments/pay-does-not-exist/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"SUCCEEDED\"}"))
                .andExpect(status().isNotFound());
    }

    private String createPayment(UUID bookingId, int amountCents) throws Exception {
        String response = mockMvc.perform(post("/internal/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":\"" + bookingId + "\",\"amountCents\":" + amountCents + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("paymentId").asText();
    }

    /** The fake server's received-list is process-wide across every test in this class, so filter by payment ID. */
    private java.util.List<FakeBookingWebhookServer.ReceivedWebhook> deliveriesFor(String paymentId) throws Exception {
        java.util.List<FakeBookingWebhookServer.ReceivedWebhook> matches = new java.util.ArrayList<>();
        for (var webhook : BOOKING_SERVER.received()) {
            if (objectMapper.readTree(webhook.body()).get("providerPaymentId").asText().equals(paymentId)) {
                matches.add(webhook);
            }
        }
        return matches;
    }

    private static String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
