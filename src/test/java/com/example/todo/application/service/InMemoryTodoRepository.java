package com.example.todo.application.service;

import com.example.todo.application.common.PageQuery;
import com.example.todo.application.common.PageResult;
import com.example.todo.application.port.out.TodoRepositoryPort;
import com.example.todo.domain.exception.TodoConflictException;
import com.example.todo.domain.model.Todo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Adapter giả (fake) để test lõi ứng dụng mà không cần Spring hay database.
 * Mô phỏng cả phiên bản (optimistic locking) như database thật.
 */
public class InMemoryTodoRepository implements TodoRepositoryPort {

    private final Map<Long, Todo> store = new TreeMap<>();
    private long sequence = 0;

    @Override
    public Todo save(Todo todo) {
        if (todo.id() == null) {
            long id = ++sequence;
            Todo saved = copy(todo, id, 0L);
            store.put(id, saved);
            return saved;
        }
        Todo existing = store.get(todo.id());
        if (existing == null || !Objects.equals(existing.version(), todo.version())) {
            throw new TodoConflictException(todo.id());
        }
        Todo saved = copy(todo, todo.id(), existing.version() + 1);
        store.put(saved.id(), saved);
        return saved;
    }

    @Override
    public Optional<Todo> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public PageResult<Todo> findAll(Boolean completed, Long ownerId, PageQuery query) {
        List<Todo> matching = new ArrayList<>(store.values().stream()
                .filter(t -> completed == null || t.completed() == completed)
                .filter(t -> ownerId == null || ownerId.equals(t.ownerId()))
                .toList());
        matching.sort(comparator(query));

        int from = Math.min(query.page() * query.size(), matching.size());
        int to = Math.min(from + query.size(), matching.size());
        return PageResult.of(matching.subList(from, to), query, matching.size());
    }

    @Override
    public void deleteById(Long id) {
        store.remove(id);
    }

    private static Todo copy(Todo todo, Long id, Long version) {
        return Todo.reconstitute(id, todo.ownerId(), todo.title(), todo.description(), todo.completed(),
                todo.createdAt(), todo.updatedAt(), version);
    }

    private static Comparator<Todo> comparator(PageQuery query) {
        Comparator<Todo> base = switch (query.sortBy()) {
            case ID -> Comparator.comparing(Todo::id);
            case TITLE -> Comparator.comparing(Todo::title, String.CASE_INSENSITIVE_ORDER);
            case CREATED_AT -> Comparator.comparing(Todo::createdAt);
            case UPDATED_AT -> Comparator.comparing(Todo::updatedAt);
        };
        Comparator<Todo> withTieBreak = base.thenComparing(Todo::id);
        return query.direction() == PageQuery.Direction.DESC ? withTieBreak.reversed() : withTieBreak;
    }
}
