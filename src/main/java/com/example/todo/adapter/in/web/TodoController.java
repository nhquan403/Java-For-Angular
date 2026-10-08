package com.example.todo.adapter.in.web;

import com.example.todo.adapter.in.web.dto.CreateTodoRequest;
import com.example.todo.adapter.in.web.dto.PageResponse;
import com.example.todo.adapter.in.web.dto.TodoResponse;
import com.example.todo.adapter.in.web.dto.UpdateTodoRequest;
import com.example.todo.application.common.Actor;
import com.example.todo.application.common.PageQuery;
import com.example.todo.application.port.in.ChangeTodoStatusUseCase;
import com.example.todo.application.port.in.CreateTodoUseCase;
import com.example.todo.application.port.in.DeleteTodoUseCase;
import com.example.todo.application.port.in.GetTodoUseCase;
import com.example.todo.application.port.in.ListTodosUseCase;
import com.example.todo.application.port.in.UpdateTodoUseCase;
import com.example.todo.domain.model.Todo;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * INBOUND ADAPTER: chuyển HTTP request thành lời gọi use case.
 * Controller chỉ phụ thuộc vào các interface (port), không biết TodoService hay JPA.
 * Mọi endpoint cần đăng nhập: controller lấy "ai đang gọi" (Actor) từ token rồi giao cho use case,
 * use case mới là nơi quyết định người đó được thấy todo nào.
 */
@RestController
@RequestMapping("/api/todos")
public class TodoController {

    private final CreateTodoUseCase createTodo;
    private final GetTodoUseCase getTodo;
    private final ListTodosUseCase listTodos;
    private final UpdateTodoUseCase updateTodo;
    private final ChangeTodoStatusUseCase changeStatus;
    private final DeleteTodoUseCase deleteTodo;

    public TodoController(CreateTodoUseCase createTodo,
                          GetTodoUseCase getTodo,
                          ListTodosUseCase listTodos,
                          UpdateTodoUseCase updateTodo,
                          ChangeTodoStatusUseCase changeStatus,
                          DeleteTodoUseCase deleteTodo) {
        this.createTodo = createTodo;
        this.getTodo = getTodo;
        this.listTodos = listTodos;
        this.updateTodo = updateTodo;
        this.changeStatus = changeStatus;
        this.deleteTodo = deleteTodo;
    }

    @PostMapping
    public ResponseEntity<TodoResponse> create(Authentication authentication,
                                               @Valid @RequestBody CreateTodoRequest request) {
        Actor actor = AuthenticatedActor.from(authentication);
        Todo todo = createTodo.create(
                new CreateTodoUseCase.Command(actor.userId(), request.title(), request.description()));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(todo.id())
                .toUri();
        return ResponseEntity.created(location).body(TodoResponse.from(todo));
    }

    /**
     * Ví dụ: GET /api/todos?completed=false&page=0&size=20&sortBy=createdAt&direction=desc
     * sortBy: id | title | createdAt | updatedAt. Tham số sai sẽ trả 400.
     * USER nhận todo của mình, ADMIN nhận tất cả.
     */
    @GetMapping
    public PageResponse<TodoResponse> list(
            Authentication authentication,
            @RequestParam(required = false) Boolean completed,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        PageQuery query = PageQuery.of(page, size, sortBy, direction);
        return PageResponse.from(
                listTodos.list(AuthenticatedActor.from(authentication), completed, query),
                TodoResponse::from);
    }

    @GetMapping("/{id}")
    public TodoResponse get(Authentication authentication, @PathVariable Long id) {
        return TodoResponse.from(getTodo.getById(AuthenticatedActor.from(authentication), id));
    }

    @PutMapping("/{id}")
    public TodoResponse update(Authentication authentication,
                               @PathVariable Long id,
                               @Valid @RequestBody UpdateTodoRequest request) {
        Todo todo = updateTodo.update(new UpdateTodoUseCase.Command(
                AuthenticatedActor.from(authentication), id,
                request.title(), request.description(), request.version()));
        return TodoResponse.from(todo);
    }

    @PatchMapping("/{id}/complete")
    public TodoResponse complete(Authentication authentication, @PathVariable Long id) {
        return TodoResponse.from(changeStatus.complete(AuthenticatedActor.from(authentication), id));
    }

    @PatchMapping("/{id}/reopen")
    public TodoResponse reopen(Authentication authentication, @PathVariable Long id) {
        return TodoResponse.from(changeStatus.reopen(AuthenticatedActor.from(authentication), id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable Long id) {
        deleteTodo.delete(AuthenticatedActor.from(authentication), id);
        return ResponseEntity.noContent().build();
    }
}
