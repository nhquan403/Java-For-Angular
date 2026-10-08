package com.example.todo.application.service;

import com.example.todo.application.port.out.TodoEventPublisherPort;
import com.example.todo.domain.event.TodoEvent;

import java.util.ArrayList;
import java.util.List;

/** Cổng phát sự kiện giả: ghi lại mọi sự kiện để test kiểm tra. */
public class RecordingEventPublisher implements TodoEventPublisherPort {

    private final List<TodoEvent> events = new ArrayList<>();

    @Override
    public void publish(TodoEvent event) {
        events.add(event);
    }

    public List<TodoEvent> events() {
        return events;
    }

    public List<TodoEvent.Type> types() {
        return events.stream().map(TodoEvent::type).toList();
    }

    public void clear() {
        events.clear();
    }
}
