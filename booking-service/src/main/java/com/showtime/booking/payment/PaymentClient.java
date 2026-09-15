package com.showtime.booking.payment;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Booking's client for the mock payment service. Registering an attempt
 * here only starts the payment process — the actual outcome always arrives
 * later, out of band, as a signed webhook (see
 * {@code PaymentWebhookController}). This call is NOT retried: creating a
 * duplicate payment attempt is not a safe operation to repeat blindly, so a
 * failure here surfaces immediately instead.
 */
@Component
public class PaymentClient {

    private final RestClient restClient;

    public PaymentClient(@Value("${showtime.payment.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @CircuitBreaker(name = "payment")
    public CreatePaymentResult createPayment(UUID bookingId, int amountCents) {
        try {
            CreatePaymentApiResponse response = restClient.post()
                    .uri("/internal/payments")
                    .body(new CreatePaymentApiRequest(bookingId, amountCents))
                    .retrieve()
                    .body(CreatePaymentApiResponse.class);
            if (response == null) {
                throw new PaymentUnavailableException("payment service returned an empty response", null);
            }
            return new CreatePaymentResult(response.paymentId());
        } catch (RestClientException ex) {
            throw new PaymentUnavailableException("payment service call failed", ex);
        }
    }

    private record CreatePaymentApiRequest(UUID bookingId, int amountCents) {
    }

    private record CreatePaymentApiResponse(String paymentId) {
    }
}
