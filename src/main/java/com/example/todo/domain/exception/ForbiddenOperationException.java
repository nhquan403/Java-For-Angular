package com.example.todo.domain.exception;

/** Người gọi đã đăng nhập nhưng không đủ quyền làm việc này (ví dụ USER gọi việc của ADMIN). */
public class ForbiddenOperationException extends RuntimeException {

    public ForbiddenOperationException(String message) {
        super(message);
    }
}
