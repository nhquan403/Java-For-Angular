package com.example.todo.application.port.in;

import com.example.todo.application.common.Actor;
import com.example.todo.domain.model.Role;
import com.example.todo.domain.model.User;

public interface ChangeUserRoleUseCase {

    /**
     * Đổi role của một user. Mọi refresh token của user đó bị thu hồi để họ phải đăng nhập lại
     * và nhận token với role mới.
     *
     * @throws com.example.todo.domain.exception.ForbiddenOperationException người gọi không phải ADMIN
     * @throws com.example.todo.domain.exception.InvalidUserException        tự đổi role của chính mình
     * @throws com.example.todo.domain.exception.UserNotFoundException       không có user đó
     */
    User changeRole(Actor actor, Long userId, Role newRole);
}
