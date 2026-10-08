package com.example.todo.domain.exception;

/** Todo đã bị người khác sửa trong lúc bạn đang sửa (xung đột phiên bản). */
public class TodoConflictException extends RuntimeException {

    public TodoConflictException(Long id) {
        super("Todo with id " + id + " was modified by someone else, reload and try again");
    }
}
