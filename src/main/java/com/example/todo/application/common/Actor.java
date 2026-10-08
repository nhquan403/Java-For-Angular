package com.example.todo.application.common;

import com.example.todo.domain.model.Todo;

import java.util.Objects;

/**
 * "Ai đang gọi": user nào, có phải ADMIN không. Lõi ứng dụng không biết JWT hay Spring Security,
 * adapter web đọc token rồi đổi thành Actor trước khi gọi use case.
 */
public record Actor(Long userId, boolean admin) {

    public Actor {
        Objects.requireNonNull(userId, "userId must not be null");
    }

    public static Actor user(Long userId) {
        return new Actor(userId, false);
    }

    public static Actor admin(Long userId) {
        return new Actor(userId, true);
    }

    /** ADMIN truy cập mọi todo, USER chỉ truy cập todo của mình. */
    public boolean canAccess(Todo todo) {
        return admin || userId.equals(todo.ownerId());
    }
}
