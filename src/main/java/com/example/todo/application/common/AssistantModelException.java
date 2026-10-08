package com.example.todo.application.common;

/**
 * Gọi mô hình AI thất bại (mạng, quá tải, sai cấu hình...). Thông báo không chứa nội dung hội thoại.
 * Xảy ra trước khi bắt đầu trả lời thì thành HTTP 503, sau đó thì thành sự kiện error.
 */
public class AssistantModelException extends RuntimeException {

    public AssistantModelException(String message, Throwable cause) {
        super(message, cause);
    }
}
