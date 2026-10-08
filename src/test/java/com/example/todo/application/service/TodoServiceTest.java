package com.example.todo.application.service;

import com.example.todo.application.common.Actor;
import com.example.todo.application.common.PageQuery;
import com.example.todo.application.common.PageResult;
import com.example.todo.application.port.in.CreateTodoUseCase;
import com.example.todo.application.port.in.UpdateTodoUseCase;
import com.example.todo.domain.event.TodoEvent;
import com.example.todo.domain.exception.InvalidTodoException;
import com.example.todo.domain.exception.TodoConflictException;
import com.example.todo.domain.exception.TodoNotFoundException;
import com.example.todo.domain.model.Todo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TodoServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-06T10:00:00Z");
    private static final PageQuery BY_ID = PageQuery.of(0, 20, "id", "asc");

    private static final Actor ALICE = Actor.user(1L);
    private static final Actor BOB = Actor.user(2L);
    private static final Actor ADMIN = Actor.admin(99L);

    private TodoService service;
    private RecordingEventPublisher events;

    @BeforeEach
    void setUp() {
        events = new RecordingEventPublisher();
        service = new TodoService(new InMemoryTodoRepository(), events, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Todo create(Actor owner, String title) {
        return service.create(new CreateTodoUseCase.Command(owner.userId(), title, null));
    }

    @Test
    void createAssignsIdOwnerAndStartsNotCompleted() {
        Todo todo = service.create(new CreateTodoUseCase.Command(1L, "  Learn Spring Boot  ", "Hexagonal"));

        assertThat(todo.id()).isEqualTo(1L);
        assertThat(todo.ownerId()).isEqualTo(1L);
        assertThat(todo.version()).isEqualTo(0L);
        assertThat(todo.title()).isEqualTo("Learn Spring Boot");
        assertThat(todo.completed()).isFalse();
        assertThat(todo.createdAt()).isEqualTo(NOW);
    }

    @Test
    void createWithBlankTitleFails() {
        assertThatThrownBy(() -> service.create(new CreateTodoUseCase.Command(1L, "   ", null)))
                .isInstanceOf(InvalidTodoException.class);
    }

    @Test
    void getByIdThrowsWhenMissing() {
        assertThatThrownBy(() -> service.getById(ALICE, 99L))
                .isInstanceOf(TodoNotFoundException.class);
    }

    @Test
    void updateChangesTitleAndDescriptionAndBumpsVersion() {
        Todo created = create(ALICE, "Old");

        Todo updated = service.update(
                new UpdateTodoUseCase.Command(ALICE, created.id(), "New", "Desc", created.version()));

        assertThat(updated.title()).isEqualTo("New");
        assertThat(updated.description()).isEqualTo("Desc");
        assertThat(updated.version()).isEqualTo(created.version() + 1);
        assertThat(service.getById(ALICE, created.id()).title()).isEqualTo("New");
    }

    @Test
    void updateWithStaleVersionIsRejected() {
        Todo created = create(ALICE, "Task");
        service.update(new UpdateTodoUseCase.Command(ALICE, created.id(), "First edit", null, created.version()));

        // client thứ hai vẫn cầm version cũ
        assertThatThrownBy(() -> service.update(
                new UpdateTodoUseCase.Command(ALICE, created.id(), "Second edit", null, created.version())))
                .isInstanceOf(TodoConflictException.class);
        assertThat(service.getById(ALICE, created.id()).title()).isEqualTo("First edit");
    }

    @Test
    void updateWithoutExpectedVersionSkipsTheCheck() {
        Todo created = create(ALICE, "Task");
        service.update(new UpdateTodoUseCase.Command(ALICE, created.id(), "First edit", null, null));

        Todo again = service.update(new UpdateTodoUseCase.Command(ALICE, created.id(), "Second edit", null, null));

        assertThat(again.title()).isEqualTo("Second edit");
    }

    @Test
    void completeAndReopenToggleStatus() {
        Todo created = create(ALICE, "Task");

        assertThat(service.complete(ALICE, created.id()).completed()).isTrue();
        assertThat(service.reopen(ALICE, created.id()).completed()).isFalse();
    }

    @Test
    void listFiltersByCompleted() {
        Todo a = create(ALICE, "A");
        create(ALICE, "B");
        service.complete(ALICE, a.id());

        assertThat(service.list(ALICE, null, BY_ID).content()).hasSize(2);
        assertThat(service.list(ALICE, true, BY_ID).content()).extracting(Todo::title).containsExactly("A");
        assertThat(service.list(ALICE, false, BY_ID).content()).extracting(Todo::title).containsExactly("B");
    }

    @Test
    void listIsPaginated() {
        for (int i = 1; i <= 5; i++) {
            create(ALICE, "Task " + i);
        }

        PageResult<Todo> lastPage = service.list(ALICE, null, PageQuery.of(2, 2, "id", "asc"));

        assertThat(lastPage.totalElements()).isEqualTo(5);
        assertThat(lastPage.totalPages()).isEqualTo(3);
        assertThat(lastPage.content()).extracting(Todo::title).containsExactly("Task 5");
    }

    @Test
    void listPageBeyondTheEndIsEmpty() {
        create(ALICE, "Only");

        PageResult<Todo> page = service.list(ALICE, null, PageQuery.of(5, 10, "id", "asc"));

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isEqualTo(1);
    }

    @Test
    void listCanSortByTitleDescending() {
        create(ALICE, "banana");
        create(ALICE, "apple");
        create(ALICE, "cherry");

        PageResult<Todo> page = service.list(ALICE, null, PageQuery.of(0, 10, "title", "desc"));

        assertThat(page.content()).extracting(Todo::title).containsExactly("cherry", "banana", "apple");
    }

    @Test
    void deleteRemovesTodoAndFailsWhenMissing() {
        Todo created = create(ALICE, "Task");

        service.delete(ALICE, created.id());

        assertThat(service.list(ALICE, null, BY_ID).content()).isEmpty();
        assertThatThrownBy(() -> service.delete(ALICE, created.id()))
                .isInstanceOf(TodoNotFoundException.class);
    }

    // ---- Quyền sở hữu ----

    @Test
    void userOnlySeesOwnTodosInList() {
        create(ALICE, "Alice 1");
        create(BOB, "Bob 1");
        create(ALICE, "Alice 2");

        assertThat(service.list(ALICE, null, BY_ID).content())
                .extracting(Todo::title).containsExactly("Alice 1", "Alice 2");
        assertThat(service.list(BOB, null, BY_ID).content())
                .extracting(Todo::title).containsExactly("Bob 1");
    }

    @Test
    void adminSeesEveryonesTodos() {
        create(ALICE, "Alice 1");
        create(BOB, "Bob 1");

        assertThat(service.list(ADMIN, null, BY_ID).content()).hasSize(2);
    }

    @Test
    void otherUsersTodoLooksLikeItDoesNotExist() {
        Todo aliceTodo = create(ALICE, "Private");

        // 404 chứ không phải 403, để Bob không dò được id nào đang tồn tại
        assertThatThrownBy(() -> service.getById(BOB, aliceTodo.id()))
                .isInstanceOf(TodoNotFoundException.class);
        assertThatThrownBy(() -> service.complete(BOB, aliceTodo.id()))
                .isInstanceOf(TodoNotFoundException.class);
        assertThatThrownBy(() -> service.reopen(BOB, aliceTodo.id()))
                .isInstanceOf(TodoNotFoundException.class);
        assertThatThrownBy(() -> service.delete(BOB, aliceTodo.id()))
                .isInstanceOf(TodoNotFoundException.class);
        assertThatThrownBy(() -> service.update(
                new UpdateTodoUseCase.Command(BOB, aliceTodo.id(), "Hacked", null, null)))
                .isInstanceOf(TodoNotFoundException.class);

        Todo untouched = service.getById(ALICE, aliceTodo.id());
        assertThat(untouched.title()).isEqualTo("Private");
        assertThat(untouched.completed()).isFalse();
    }

    @Test
    void adminCanReadAndModifyAnyTodo() {
        Todo aliceTodo = create(ALICE, "Alice's");

        assertThat(service.getById(ADMIN, aliceTodo.id()).title()).isEqualTo("Alice's");
        assertThat(service.complete(ADMIN, aliceTodo.id()).completed()).isTrue();
        service.delete(ADMIN, aliceTodo.id());
        assertThatThrownBy(() -> service.getById(ALICE, aliceTodo.id()))
                .isInstanceOf(TodoNotFoundException.class);
    }

    // ---- Sự kiện ----

    @Test
    void everyRealChangePublishesOneEventWithTheRightOwner() {
        Todo todo = create(ALICE, "Task");
        service.update(new UpdateTodoUseCase.Command(ALICE, todo.id(), "Task v2", null, null));
        service.complete(ALICE, todo.id());
        service.reopen(ALICE, todo.id());
        service.delete(ALICE, todo.id());

        assertThat(events.types()).containsExactly(
                TodoEvent.Type.CREATED, TodoEvent.Type.UPDATED, TodoEvent.Type.COMPLETED,
                TodoEvent.Type.REOPENED, TodoEvent.Type.DELETED);
        assertThat(events.events()).extracting(TodoEvent::ownerId).containsExactly(1L, 1L, 1L, 1L, 1L);
        assertThat(events.events()).extracting(TodoEvent::todoId).containsExactly(1L, 1L, 1L, 1L, 1L);
        assertThat(events.events().get(0).occurredAt()).isEqualTo(NOW);
    }

    @Test
    void eventCarriesTheNewStateOfTheTodo() {
        Todo todo = create(ALICE, "Task");
        service.complete(ALICE, todo.id());

        TodoEvent completed = events.events().get(1);
        assertThat(completed.completed()).isTrue();
        assertThat(completed.title()).isEqualTo("Task");
    }

    @Test
    void operationsThatChangeNothingPublishNothing() {
        Todo todo = create(ALICE, "Task");
        events.clear();

        service.reopen(ALICE, todo.id());          // chưa hoàn thành nên mở lại không đổi gì
        service.complete(ALICE, todo.id());
        events.clear();
        service.complete(ALICE, todo.id());        // đã hoàn thành rồi

        assertThat(events.events()).isEmpty();
    }

    @Test
    void failedOperationsPublishNothing() {
        Todo aliceTodo = create(ALICE, "Private");
        events.clear();

        assertThatThrownBy(() -> service.complete(BOB, aliceTodo.id())).isInstanceOf(TodoNotFoundException.class);
        assertThatThrownBy(() -> service.delete(BOB, aliceTodo.id())).isInstanceOf(TodoNotFoundException.class);
        assertThatThrownBy(() -> service.create(new CreateTodoUseCase.Command(1L, " ", null)))
                .isInstanceOf(InvalidTodoException.class);
        assertThatThrownBy(() -> service.update(
                new UpdateTodoUseCase.Command(ALICE, aliceTodo.id(), "x", null, 99L)))
                .isInstanceOf(TodoConflictException.class);

        assertThat(events.events()).isEmpty();
    }

    @Test
    void adminActionOnSomeonesTodoStillNotifiesTheOwner() {
        Todo aliceTodo = create(ALICE, "Alice's");
        events.clear();

        service.complete(ADMIN, aliceTodo.id());

        assertThat(events.events().get(0).ownerId()).isEqualTo(ALICE.userId());
    }
}
