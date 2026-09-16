package com.showtime.gateway.support;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Stands in for one of the real downstream services (Identity, Catalog,
 * Booking) in gateway routing tests. Responds to every request with a JSON
 * body naming itself and the exact path it received, so a test can assert
 * both "which service did the gateway route to" and "what path did it
 * rewrite the request to" in one call — and records every received
 * request so header propagation (the correlation ID) can be asserted too.
 */
public class FakeDownstreamServer {

    public record ReceivedRequest(String method, String path, String correlationHeader) {
    }

    private final HttpServer server;
    private final String serviceName;
    private final List<ReceivedRequest> received = new CopyOnWriteArrayList<>();

    public FakeDownstreamServer(String serviceName) throws IOException {
        this.serviceName = serviceName;
        this.server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String correlationHeader = exchange.getRequestHeaders().getFirst("X-Request-Id");
            received.add(new ReceivedRequest(exchange.getRequestMethod(), path, correlationHeader));

            String body = "{\"service\":\"" + serviceName + "\",\"path\":\"" + path + "\"}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    public String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    public List<ReceivedRequest> received() {
        return Collections.unmodifiableList(received);
    }

    public void stop() {
        server.stop(0);
    }
}
