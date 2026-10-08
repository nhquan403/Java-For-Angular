package com.example.todo.application.port.in;

import com.example.todo.application.common.Actor;
import com.example.todo.application.common.PageResult;
import com.example.todo.domain.model.User;

public interface ListUsersUseCase {

    /** @throws com.example.todo.domain.exception.ForbiddenOperationException nếu người gọi không phải ADMIN */
    PageResult<User> list(Actor actor, int page, int size);
}
