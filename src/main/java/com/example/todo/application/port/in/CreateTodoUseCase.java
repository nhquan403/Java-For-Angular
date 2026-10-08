package com.example.todo.application.port.in;

import com.example.todo.domain.model.Todo;

/** INBOUND PORT: thế giới bên ngoài (web, CLI...) gọi vào ứng dụng qua interface này. */
public interface CreateTodoUseCase {

    Todo create(Command command);

    /** @param ownerId user sở hữu todo mới (lấy từ người đang đăng nhập) */
    record Command(Long ownerId, String title, String description) {
    }
}
