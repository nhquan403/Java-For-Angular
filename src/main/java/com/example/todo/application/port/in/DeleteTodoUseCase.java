package com.example.todo.application.port.in;

import com.example.todo.application.common.Actor;

public interface DeleteTodoUseCase {

    void delete(Actor actor, Long id);
}
