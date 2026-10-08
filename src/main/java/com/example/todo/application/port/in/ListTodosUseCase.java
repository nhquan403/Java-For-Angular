package com.example.todo.application.port.in;

import com.example.todo.application.common.Actor;
import com.example.todo.application.common.PageQuery;
import com.example.todo.application.common.PageResult;
import com.example.todo.domain.model.Todo;

public interface ListTodosUseCase {

    /**
     * USER chỉ nhận todo của mình, ADMIN nhận tất cả.
     *
     * @param completed null = lấy tất cả, true/false = lọc theo trạng thái
     * @param query     phân trang và sắp xếp
     */
    PageResult<Todo> list(Actor actor, Boolean completed, PageQuery query);
}
