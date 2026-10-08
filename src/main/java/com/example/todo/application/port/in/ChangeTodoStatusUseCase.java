package com.example.todo.application.port.in;

import com.example.todo.application.common.Actor;
import com.example.todo.domain.model.Todo;

public interface ChangeTodoStatusUseCase {

    Todo complete(Actor actor, Long id);

    Todo reopen(Actor actor, Long id);
}
