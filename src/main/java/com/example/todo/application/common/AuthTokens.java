package com.example.todo.application.common;

import java.time.Instant;

/** Cặp token trả cho client sau khi đăng nhập hoặc làm mới. */
public record AuthTokens(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt) {

    /** Không in giá trị token ra log. */
    @Override
    public String toString() {
        return "AuthTokens[accessTokenExpiresAt=" + accessTokenExpiresAt
                + ", refreshTokenExpiresAt=" + refreshTokenExpiresAt + "]";
    }
}
