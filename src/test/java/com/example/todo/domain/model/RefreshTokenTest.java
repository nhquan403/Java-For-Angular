package com.example.todo.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenTest {

    private static final Instant NOW = Instant.parse("2026-10-06T10:00:00Z");

    @Test
    void issuedTokenExpiresAfterTtlAndIsNotRevoked() {
        RefreshToken token = RefreshToken.issue(1L, "hash", NOW, Duration.ofDays(7));

        assertThat(token.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(token.isRevoked()).isFalse();
    }

    @Test
    void expiryIsInclusiveOfTheExactInstant() {
        RefreshToken token = RefreshToken.issue(1L, "hash", NOW, Duration.ofHours(1));

        assertThat(token.isExpired(NOW.plusSeconds(3599))).isFalse();
        assertThat(token.isExpired(NOW.plusSeconds(3600))).isTrue();
    }
}
