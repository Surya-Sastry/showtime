package com.showtime.payment.support;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Stands in for Booking's {@code /internal/webhooks/payments} endpoint in
 * payment-service integration tests, capturing every delivery so tests can
 * assert exactly what was sent and that it was signed correctly — without
 * needing a real Booking instance running.
 */
public class FakeBookingWebhookServer {

    public record ReceivedWebhook(String body, String signature) {
    }

    private final HttpServer server;
    private final List<ReceivedWebhook> received = new CopyOnWriteArrayList<>();

    public FakeBookingWebhookServer() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/internal/webhooks/payments", exchange -> {
            byte[] bodyBytes = exchange.getRequestBody().readAllBytes();
            String body = new String(bodyBytes, StandardCharsets.UTF_8);
            String signature = exchange.getRequestHeaders().getFirst("X-Signature");
            received.add(new ReceivedWebhook(body, signature));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
    }

    public String url() {
        return "http://localhost:" + server.getAddress().getPort() + "/internal/webhooks/payments";
    }

    public List<ReceivedWebhook> received() {
        return Collections.unmodifiableList(received);
    }

    public void stop() {
        server.stop(0);
    }
}
