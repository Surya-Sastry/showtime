package com.showtime.booking.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtVerifier jwtVerifier) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Health checks and the payment webhook are the only
                        // unauthenticated entry points. The webhook is instead
                        // guarded by its own HMAC signature check
                        // (WebhookSignatureVerifier) — deliberately not JWT,
                        // since the caller is the payment service, not a user.
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/internal/webhooks/**").permitAll()
                        // Deny by default: every hold/booking endpoint requires
                        // an authenticated caller, and ownership is enforced in
                        // the service layer on top of that.
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtVerifier), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
