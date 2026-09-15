package com.showtime.catalog.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
                        // Browsing the catalog is public; only schedule creation is manager-only.
                        .requestMatchers(HttpMethod.POST, "/shows").hasRole("THEATER_MANAGER")
                        .anyRequest().permitAll())
                .addFilterBefore(new JwtAuthenticationFilter(jwtVerifier), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
