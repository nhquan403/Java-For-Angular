package com.example.todo.adapter.out.persistence;

import com.example.todo.application.common.PageQuery;
import com.example.todo.application.common.PageResult;
import com.example.todo.application.port.out.TodoRepositoryPort;
import com.example.todo.domain.exception.TodoConflictException;
import com.example.todo.domain.model.Todo;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * OUTBOUND ADAPTER: hiện thực TodoRepositoryPort bằng Spring Data JPA.
 * Muốn đổi sang MongoDB/file: viết adapter mới, lõi ứng dụng không phải sửa.
 */
@Component
class TodoPersistenceAdapter implements TodoRepositoryPort {

    private final SpringDataTodoRepository repository;

    TodoPersistenceAdapter(SpringDataTodoRepository repository) {
        this.repository = repository;
    }

    @Override
    public Todo save(Todo todo) {
        try {
            // saveAndFlush: ép chạy câu UPDATE ngay tại đây để bắt xung đột phiên bản
            // trong try/catch này, thay vì để nó nổ muộn lúc commit giao dịch.
            return toDomain(repository.saveAndFlush(toEntity(todo)));
        } catch (OptimisticLockingFailureException e) {
            // Dịch lỗi hạ tầng (Spring/Hibernate) thành lỗi của domain.
            throw new TodoConflictException(todo.id());
        }
    }

    @Override
    public Optional<Todo> findById(Long id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public PageResult<Todo> findAll(Boolean completed, Long ownerId, PageQuery query) {
        Pageable pageable = PageRequest.of(query.page(), query.size(), toSort(query));
        Page<TodoJpaEntity> page;
        if (ownerId == null) {
            page = completed == null
                    ? repository.findAll(pageable)
                    : repository.findByCompleted(completed, pageable);
        } else {
            page = completed == null
                    ? repository.findByOwnerId(ownerId, pageable)
                    : repository.findByOwnerIdAndCompleted(ownerId, completed, pageable);
        }
        return PageResult.of(
                page.getContent().stream().map(this::toDomain).toList(),
                query,
                page.getTotalElements());
    }

    @Override
    public void deleteById(Long id) {
        repository.deleteById(id);
    }

    /** Luôn thêm id làm tiêu chí phụ để thứ tự ổn định, tránh lặp/sót phần tử giữa các trang. */
    private Sort toSort(PageQuery query) {
        Sort.Direction direction = query.direction() == PageQuery.Direction.ASC
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        String property = switch (query.sortBy()) {
            case ID -> "id";
            case TITLE -> "title";
            case CREATED_AT -> "createdAt";
            case UPDATED_AT -> "updatedAt";
        };
        Sort sort = Sort.by(direction, property);
        return query.sortBy() == PageQuery.SortField.ID ? sort : sort.and(Sort.by(direction, "id"));
    }

    private TodoJpaEntity toEntity(Todo todo) {
        return new TodoJpaEntity(todo.id(), todo.ownerId(), todo.title(), todo.description(),
                todo.completed(), todo.createdAt(), todo.updatedAt(), todo.version());
    }

    private Todo toDomain(TodoJpaEntity entity) {
        return Todo.reconstitute(entity.getId(), entity.getOwnerId(), entity.getTitle(),
                entity.getDescription(), entity.isCompleted(), entity.getCreatedAt(),
                entity.getUpdatedAt(), entity.getVersion());
    }
}
