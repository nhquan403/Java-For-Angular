package com.example.todo.application.common;

/** Một người dùng mở quá nhiều kết nối thời gian thực cùng lúc. Thành HTTP 429. */
public class TooManySubscriptionsException extends RuntimeException {

    public TooManySubscriptionsException(int max) {
        super("Too many realtime connections, the limit is " + max + " per user");
    }
}
