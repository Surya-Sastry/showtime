package com.showtime.booking.catalog;

import io.grpc.ClientInterceptor;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetry;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Injects the current trace into gRPC metadata on every {@code CatalogClient}
 * call, so Catalog's matching server interceptor continues the same trace
 * instead of starting an unrelated one.
 */
@Configuration
public class GrpcTracingConfig {

    @Bean
    @GrpcGlobalClientInterceptor
    public ClientInterceptor otelClientInterceptor(OpenTelemetry openTelemetry) {
        return GrpcTelemetry.create(openTelemetry).createClientInterceptor();
    }
}
