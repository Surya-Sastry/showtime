package com.showtime.identity.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void hashesAreNotThePlaintextPassword() {
        String hash = hasher.hash("correct-horse-battery-staple");
        assertThat(hash).doesNotContain("correct-horse-battery-staple");
    }

    @Test
    void matchesReturnsTrueForTheOriginalPassword() {
        String hash = hasher.hash("correct-horse-battery-staple");
        assertThat(hasher.matches("correct-horse-battery-staple", hash)).isTrue();
    }

    @Test
    void matchesReturnsFalseForAWrongPassword() {
        String hash = hasher.hash("correct-horse-battery-staple");
        assertThat(hasher.matches("wrong-password", hash)).isFalse();
    }

    @Test
    void hashingTheSamePasswordTwiceProducesDifferentHashes() {
        // Argon2id salts each hash independently; equal plaintexts must not
        // produce equal hashes, otherwise identical passwords would be
        // detectable by comparing stored hashes across accounts.
        String first = hasher.hash("correct-horse-battery-staple");
        String second = hasher.hash("correct-horse-battery-staple");
        assertThat(first).isNotEqualTo(second);
    }
}
