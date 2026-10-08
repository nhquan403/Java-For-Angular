package com.example.todo.application.common;

/** Người dùng hỏi trợ lý quá số lần cho phép trong một phút. Thành HTTP 429. */
public class TooManyAssistantRequestsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyAssistantRequestsException(int limitPerMinute, long retryAfterSeconds) {
        super("Too many assistant requests, the limit is " + limitPerMinute + " per minute");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /** Số giây nên chờ trước khi hỏi lại. */
    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
