package com.example.todo.application.common;

/** Tham số phân trang không hợp lệ (page âm, size quá lớn, sortBy lạ...). */
public class InvalidPageQueryException extends RuntimeException {

    public InvalidPageQueryException(String message) {
        super(message);
    }
}
