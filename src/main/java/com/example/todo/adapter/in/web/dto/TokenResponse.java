package com.example.todo.adapter.in.web.dto;

import com.example.todo.application.common.AuthTokens;

import java.time.Instant;

/** Cặp token trả về sau khi đăng nhập hoặc làm mới. */
public record TokenResponse(
        String tokenType,
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt) {

    public static TokenResponse from(AuthTokens tokens) {
        return new TokenResponse("Bearer", tokens.accessToken(), tokens.accessTokenExpiresAt(),
                tokens.refreshToken(), tokens.refreshTokenExpiresAt());
    }

    /** Không in token ra log. */
    @Override
    public String toString() {
        return "TokenResponse[accessTokenExpiresAt=" + accessTokenExpiresAt
                + ", refreshTokenExpiresAt=" + refreshTokenExpiresAt + "]";
    }
}
