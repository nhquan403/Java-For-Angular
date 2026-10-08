package com.example.todo.application.common;

/** Yêu cầu chat với trợ lý không hợp lệ (quá nhiều tin, tin quá dài, role lạ...). Thành HTTP 400. */
public class InvalidAssistantRequestException extends RuntimeException {

    public InvalidAssistantRequestException(String message) {
        super(message);
    }
}
