package com.example.todo.adapter.in.web.dto;

import com.example.todo.domain.model.Todo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTodoRequest(
        @NotBlank(message = "title must not be blank")
        @Size(max = Todo.MAX_TITLE_LENGTH, message = "title must not exceed {max} characters")
        String title,

        @Size(max = Todo.MAX_DESCRIPTION_LENGTH, message = "description must not exceed {max} characters")
        String description) {
}
