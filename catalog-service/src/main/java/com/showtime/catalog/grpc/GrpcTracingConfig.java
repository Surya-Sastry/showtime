package com.showtime.catalog.grpc;

import io.grpc.ServerInterceptor;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetry;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Continues the trace Booking's matching client interceptor propagates in
 * gRPC metadata, so every {@code CatalogGrpcService} RPC shows up as a
 * child span of the call that triggered it instead of an untraced gap.
 */
@Configuration
public class GrpcTracingConfig {

    @Bean
    @GrpcGlobalServerInterceptor
    public ServerInterceptor otelServerInterceptor(OpenTelemetry openTelemetry) {
        return GrpcTelemetry.create(openTelemetry).createServerInterceptor();
    }
}
