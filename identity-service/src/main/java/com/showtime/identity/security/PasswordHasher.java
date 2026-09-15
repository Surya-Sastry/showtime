package com.showtime.identity.security;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Wraps Spring Security's Argon2id encoder. Per project security requirements
 * we never use MD5/SHA-1/plain storage for credentials — Argon2id is a
 * memory-hard KDF designed for password storage. Parameters below match
 * Spring Security's own "current" recommended defaults as of Spring Security
 * 6.x (16-byte salt, 32-byte hash, 1 iteration, 5 threads, 1<<14 KB memory);
 * kept explicit rather than relying on a version-pinned convenience factory
 * so the cost parameters are visible and reviewable here.
 */
@Component
public class PasswordHasher {

    private final Argon2PasswordEncoder encoder =
            new Argon2PasswordEncoder(16, 32, 5, 1 << 14, 1);

    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String hash) {
        return encoder.matches(rawPassword, hash);
    }
}
