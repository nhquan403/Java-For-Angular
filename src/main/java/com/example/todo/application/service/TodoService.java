package com.example.todo.application.service;

import com.example.todo.application.common.Actor;
import com.example.todo.application.common.PageQuery;
import com.example.todo.application.common.PageResult;
import com.example.todo.application.port.in.ChangeTodoStatusUseCase;
import com.example.todo.application.port.in.CreateTodoUseCase;
import com.example.todo.application.port.in.DeleteTodoUseCase;
import com.example.todo.application.port.in.GetTodoUseCase;
import com.example.todo.application.port.in.ListTodosUseCase;
import com.example.todo.application.port.in.UpdateTodoUseCase;
import com.example.todo.application.port.out.TodoEventPublisherPort;
import com.example.todo.application.port.out.TodoRepositoryPort;
import com.example.todo.domain.exception.TodoConflictException;
import com.example.todo.domain.event.TodoEvent;
import com.example.todo.domain.exception.TodoNotFoundException;
import com.example.todo.domain.model.Todo;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Hiện thực các use case về todo. Chỉ biết domain + outbound port, không biết JPA hay HTTP.
 * Được khai báo thành bean ở config/BeanConfig.
 *
 * Quy tắc quyền sở hữu nằm ở đây (không nằm ở controller): USER chỉ truy cập todo của mình,
 * ADMIN truy cập tất cả. Todo của người khác trả về "không tìm thấy" (404) thay vì "cấm" (403),
 * để người lạ không dò được id nào đang tồn tại.
 *
 * Mỗi thay đổi thật sự đều phát một TodoEvent qua cổng ra TodoEventPublisherPort. Thao tác không làm
 * đổi gì (ví dụ complete một todo đã hoàn thành) thì không phát sự kiện.
 *
 * Ngoại lệ duy nhất với Spring: @Transactional, ArchitectureTest cho phép riêng gói này.
 */
@Transactional
public class TodoService implements
        CreateTodoUseCase,
        GetTodoUseCase,
        ListTodosUseCase,
        UpdateTodoUseCase,
        ChangeTodoStatusUseCase,
        DeleteTodoUseCase {

    private final TodoRepositoryPort repository;
    private final TodoEventPublisherPort events;
    private final Clock clock;

    public TodoService(TodoRepositoryPort repository, TodoEventPublisherPort events, Clock clock) {
        this.repository = repository;
        this.events = events;
        this.clock = clock;
    }

    @Override
    public Todo create(CreateTodoUseCase.Command command) {
        Todo todo = Todo.create(command.ownerId(), command.title(), command.description(), now());
        return saveAndPublish(todo, TodoEvent.Type.CREATED);
    }

    @Override
    @Transactional(readOnly = true)
    public Todo getById(Actor actor, Long id) {
        return findAccessible(actor, id);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Todo> list(Actor actor, Boolean completed, PageQuery query) {
        Long ownerFilter = actor.admin() ? null : actor.userId();
        return repository.findAll(completed, ownerFilter, query);
    }

    @Override
    public Todo update(UpdateTodoUseCase.Command command) {
        Todo current = findAccessible(command.actor(), command.id());
        if (command.expectedVersion() != null && !command.expectedVersion().equals(current.version())) {
            throw new TodoConflictException(command.id());
        }
        Todo updated = current.update(command.title(), command.description(), now());
        return saveAndPublish(updated, TodoEvent.Type.UPDATED);
    }

    @Override
    public Todo complete(Actor actor, Long id) {
        Todo current = findAccessible(actor, id);
        Todo done = current.complete(now());
        return done == current ? current : saveAndPublish(done, TodoEvent.Type.COMPLETED);
    }

    @Override
    public Todo reopen(Actor actor, Long id) {
        Todo current = findAccessible(actor, id);
        Todo reopened = current.reopen(now());
        return reopened == current ? current : saveAndPublish(reopened, TodoEvent.Type.REOPENED);
    }

    @Override
    public void delete(Actor actor, Long id) {
        Todo todo = findAccessible(actor, id);
        repository.deleteById(id);
        events.publish(TodoEvent.of(TodoEvent.Type.DELETED, todo, now()));
    }

    private Todo saveAndPublish(Todo todo, TodoEvent.Type type) {
        Todo saved = repository.save(todo);
        events.publish(TodoEvent.of(type, saved, now()));
        return saved;
    }

    private Todo findAccessible(Actor actor, Long id) {
        Todo todo = repository.findById(id).orElseThrow(() -> new TodoNotFoundException(id));
        if (!actor.canAccess(todo)) {
            throw new TodoNotFoundException(id);
        }
        return todo;
    }

    private Instant now() {
        return clock.instant();
    }
}
