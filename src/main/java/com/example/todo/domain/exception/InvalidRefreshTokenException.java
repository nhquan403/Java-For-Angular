package com.example.todo.domain.exception;

/** Refresh token sai, hết hạn, đã bị thu hồi hoặc đã dùng rồi. */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Refresh token is invalid, expired or already used");
    }
}
