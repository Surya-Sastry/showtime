package com.showtime.booking.support;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A real (loopback) HTTP server standing in for the mock payment service's
 * {@code POST /internal/payments} endpoint, so {@code PaymentClient}'s
 * integration tests exercise a real HTTP round trip. Uses only the JDK's
 * built-in {@code com.sun.net.httpserver} — no extra test dependency.
 */
public class FakePaymentServer {

    private final HttpServer server;
    private final AtomicInteger requestCount = new AtomicInteger();

    public FakePaymentServer() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/internal/payments", exchange -> {
            requestCount.incrementAndGet();
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            String body = "{\"paymentId\":\"pay-" + UUID.randomUUID() + "\"}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public int requestCount() {
        return requestCount.get();
    }

    public void stop() {
        server.stop(0);
    }
}
