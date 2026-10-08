package com.example.todo.application.common;

import java.time.Instant;

/** Access token (JWT) vừa phát và thời điểm hết hạn của nó. */
public record AccessToken(String value, Instant expiresAt) {

    /** Không in giá trị token ra log. */
    @Override
    public String toString() {
        return "AccessToken[expiresAt=" + expiresAt + "]";
    }
}
