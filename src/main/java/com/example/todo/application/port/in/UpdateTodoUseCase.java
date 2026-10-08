package com.example.todo.application.port.in;

import com.example.todo.application.common.Actor;
import com.example.todo.domain.model.Todo;

public interface UpdateTodoUseCase {

    /** @throws com.example.todo.domain.exception.TodoConflictException nếu expectedVersion không khớp */
    Todo update(Command command);

    /**
     * @param expectedVersion phiên bản client đang giữ. Null = bỏ qua kiểm tra
     *                        (vẫn được database bảo vệ khỏi ghi đè đồng thời).
     */
    record Command(Actor actor, Long id, String title, String description, Long expectedVersion) {
    }
}
