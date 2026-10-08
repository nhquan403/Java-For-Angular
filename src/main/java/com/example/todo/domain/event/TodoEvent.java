package com.example.todo.domain.event;

import com.example.todo.domain.model.Todo;

import java.time.Instant;
import java.util.Objects;

/**
 * Sự kiện domain: "có một todo vừa thay đổi". Là một sự thật đã xảy ra nên bất biến và đặt tên ở quá khứ.
 * Chỉ mang dữ liệu tối thiểu để client biết cần làm mới cái gì, không mang toàn bộ todo.
 */
public record TodoEvent(Type type, Long todoId, Long ownerId, String title, boolean completed, Instant occurredAt) {

    public enum Type {
        CREATED, UPDATED, COMPLETED, REOPENED, DELETED
    }

    public TodoEvent {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(todoId, "todoId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    /**
     * ownerId có thể null với todo cũ tạo trước khi có đăng nhập. Khi đó chỉ ADMIN nhận được sự kiện
     * (xem RealtimeService), giống quy tắc truy cập todo.
     */
    public static TodoEvent of(Type type, Todo todo, Instant occurredAt) {
        return new TodoEvent(type, todo.id(), todo.ownerId(), todo.title(), todo.completed(), occurredAt);
    }
}
