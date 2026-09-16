package com.showtime.payment.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.retry.annotation.Retry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Delivers the signed webhook to Booking's {@code /internal/webhooks/payments}.
 * Bounded retries are safe here specifically because delivery is keyed by
 * {@code eventId}: Booking's UNIQUE(event_id) constraint makes a retried
 * delivery a no-op on the receiving end, so retrying a slow/flaky delivery
 * cannot double-apply its effect.
 */
@Component
public class BookingWebhookClient {

    private final RestClient restClient;
    private final WebhookSigner signer;
    private final ObjectMapper objectMapper;

    public BookingWebhookClient(
            @Value("${showtime.webhook.booking-callback-url}") String bookingCallbackUrl,
            WebhookSigner signer,
            ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = RestClient.builder()
                .baseUrl(bookingCallbackUrl)
                .requestFactory(requestFactory)
                .build();
        this.signer = signer;
        this.objectMapper = objectMapper;
    }

    @Retry(name = "bookingWebhook")
    public void deliver(OutgoingWebhookPayload payload) {
        try {
            String body = objectMapper.writeValueAsString(payload);
            String signature = signer.sign(body.getBytes(StandardCharsets.UTF_8));
            restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Signature", signature)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            throw new WebhookDeliveryException("failed to deliver payment webhook to Booking", ex);
        }
    }
}
