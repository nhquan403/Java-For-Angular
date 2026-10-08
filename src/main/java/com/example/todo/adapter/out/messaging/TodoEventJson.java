package com.example.todo.adapter.out.messaging;

import com.example.todo.application.common.JsonText;
import com.example.todo.domain.event.TodoEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Định dạng JSON của sự kiện gửi cho client (qua Kafka, SSE và WebSocket đều dùng chung một chuỗi này).
 * Chỉ có 6 trường cố định nên dùng JsonText (viết JSON tay, có thoát ký tự) thay vì phụ thuộc Jackson.
 */
public final class TodoEventJson {

    private TodoEventJson() {
    }

    public static String toJson(TodoEvent event) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("type", event.type().name());
        json.put("todoId", event.todoId());
        json.put("ownerId", event.ownerId());
        json.put("title", event.title());
        json.put("completed", event.completed());
        json.put("occurredAt", event.occurredAt());
        return JsonText.write(json);
    }
}
