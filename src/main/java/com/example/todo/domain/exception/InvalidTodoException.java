package com.example.todo.domain.exception;

/** Vi phạm quy tắc nghiệp vụ của Todo (ví dụ title rỗng). */
public class InvalidTodoException extends RuntimeException {

    public InvalidTodoException(String message) {
        super(message);
    }
}
