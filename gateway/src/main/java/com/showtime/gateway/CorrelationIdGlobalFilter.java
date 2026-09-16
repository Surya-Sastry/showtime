package com.showtime.gateway;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * The gateway is the one place a request ID is guaranteed to exist for
 * every request that enters the system, so it is the natural place to
 * originate it. Per the README's operations requirement ("Correlation ID
 * propagates through gateway, REST, gRPC, and logs"):
 *
 * <ul>
 *   <li>if the caller already sent one, it's kept as-is (so a client-side
 *       trace ID survives the hop);</li>
 *   <li>otherwise a fresh one is generated here;</li>
 *   <li>either way, it's forwarded to whichever downstream service the
 *       route sends the request to, and echoed back to the caller in the
 *       response — so the same ID can be grepped across the gateway's log,
 *       every downstream service's log, and the client's own records.</li>
 * </ul>
 *
 * <p>Runs at {@link Ordered#HIGHEST_PRECEDENCE} so every other filter and
 * the eventual proxy call see the header already set.
 */
@Component
public class CorrelationIdGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdGlobalFilter.class);

    private final String headerName;

    public CorrelationIdGlobalFilter(@Value("${showtime.correlation.header-name}") String headerName) {
        this.headerName = headerName;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(headerName);
        String correlationId = (incoming == null || incoming.isBlank()) ? UUID.randomUUID().toString() : incoming;

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(headerName, correlationId)
                .build();
        ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();
        mutatedExchange.getResponse().getHeaders().set(headerName, correlationId);

        log.info("[{}] {} {}", correlationId, exchange.getRequest().getMethod(), exchange.getRequest().getPath());

        return chain.filter(mutatedExchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
