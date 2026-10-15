package com.neueda.leap.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The API refuses to start with a JWT_SECRET anyone could know (OWASP A02-5).
 */
class SecurityConfigSecretTest {

    @Test
    @DisplayName("accepts a random secret of at least 32 bytes")
    void acceptsARealSecret() {
        assertThatCode(() -> SecurityConfig.checkSecret("a".repeat(64))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rejects a missing or short secret")
    void rejectsShortSecret() {
        assertThatThrownBy(() -> SecurityConfig.checkSecret(null)).hasMessageContaining("at least 32 bytes");
        assertThatThrownBy(() -> SecurityConfig.checkSecret("short")).hasMessageContaining("at least 32 bytes");
    }

    @Test
    @DisplayName("rejects the .env.example placeholder, which is long enough to pass the length check")
    void rejectsPlaceholder() {
        assertThatThrownBy(() -> SecurityConfig.checkSecret("change-me-to-a-random-value-of-at-least-32-bytes"))
                .hasMessageContaining("placeholder");
    }
}
