package com.example.todo.application.port.in;

import com.example.todo.domain.model.User;

public interface GetUserUseCase {

    /** @throws com.example.todo.domain.exception.UserNotFoundException nếu không tồn tại */
    User getById(Long id);
}
