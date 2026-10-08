package com.example.todo.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Refresh token đã phát cho một user. Domain chỉ giữ bản BĂM (tokenHash) của token,
 * giá trị gốc chỉ client biết. Nếu database bị lộ, kẻ xấu cũng không dùng được token.
 *
 * Mỗi token chỉ dùng được một lần: khi làm mới, token cũ bị thu hồi (revokedAt) và
 * phát token mới (xoay vòng). Dùng lại token đã thu hồi nghĩa là có thể đã bị đánh cắp.
 */
public final class RefreshToken {

    private final Long id;
    private final Long userId;
    private final String tokenHash;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final Instant revokedAt;

    private RefreshToken(Long id, Long userId, String tokenHash,
                         Instant createdAt, Instant expiresAt, Instant revokedAt) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
    }

    public static RefreshToken issue(Long userId, String tokenHash, Instant now, Duration ttl) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(tokenHash, "tokenHash must not be null");
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(ttl, "ttl must not be null");
        return new RefreshToken(null, userId, tokenHash, now, now.plus(ttl), null);
    }

    public static RefreshToken reconstitute(Long id, Long userId, String tokenHash,
                                            Instant createdAt, Instant expiresAt, Instant revokedAt) {
        return new RefreshToken(id, userId, tokenHash, createdAt, expiresAt, revokedAt);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public Long id() { return id; }
    public Long userId() { return userId; }
    public String tokenHash() { return tokenHash; }
    public Instant createdAt() { return createdAt; }
    public Instant expiresAt() { return expiresAt; }
    public Instant revokedAt() { return revokedAt; }
}
