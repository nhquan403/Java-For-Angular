package com.example.todo.adapter.in.web.dto;

import com.example.todo.domain.model.Todo;

import java.time.Instant;

public record TodoResponse(
        Long id,
        String title,
        String description,
        boolean completed,
        Instant createdAt,
        Instant updatedAt,
        Long version) {

    public static TodoResponse from(Todo todo) {
        return new TodoResponse(
                todo.id(),
                todo.title(),
                todo.description(),
                todo.completed(),
                todo.createdAt(),
                todo.updatedAt(),
                todo.version());
    }
}
