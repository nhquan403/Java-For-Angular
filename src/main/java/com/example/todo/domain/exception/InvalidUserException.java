package com.example.todo.domain.exception;

/** Dữ liệu user không hợp lệ (email sai, mật khẩu yếu, đổi role của chính mình...). */
public class InvalidUserException extends RuntimeException {

    public InvalidUserException(String message) {
        super(message);
    }
}
