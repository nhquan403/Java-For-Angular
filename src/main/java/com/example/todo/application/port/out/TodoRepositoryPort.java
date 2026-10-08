package com.example.todo.application.port.out;

import com.example.todo.application.common.PageQuery;
import com.example.todo.application.common.PageResult;
import com.example.todo.domain.model.Todo;

import java.util.Optional;

/**
 * OUTBOUND PORT: ứng dụng cần "kho lưu trữ" nhưng không biết nó là JPA, Mongo hay file.
 * Phần triển khai (adapter) nằm ở adapter/out/persistence.
 */
public interface TodoRepositoryPort {

    /**
     * Lưu mới hoặc cập nhật.
     *
     * @throws com.example.todo.domain.exception.TodoConflictException nếu bản ghi đã bị
     *                                                                  người khác sửa (version lệch)
     */
    Todo save(Todo todo);

    Optional<Todo> findById(Long id);

    /**
     * @param completed null = tất cả, true/false = lọc theo trạng thái
     * @param ownerId   null = của mọi người (dành cho ADMIN), có giá trị = chỉ của user đó
     */
    PageResult<Todo> findAll(Boolean completed, Long ownerId, PageQuery query);

    void deleteById(Long id);
}
