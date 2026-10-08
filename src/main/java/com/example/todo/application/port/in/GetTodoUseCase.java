package com.example.todo.application.port.in;

import com.example.todo.application.common.Actor;
import com.example.todo.domain.model.Todo;

public interface GetTodoUseCase {

    /**
     * @throws com.example.todo.domain.exception.TodoNotFoundException nếu không tồn tại,
     *                                                                  hoặc todo của người khác (không tiết lộ là có tồn tại)
     */
    Todo getById(Actor actor, Long id);
}
