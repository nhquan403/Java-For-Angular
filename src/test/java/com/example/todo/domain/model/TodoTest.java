package com.example.todo.domain.model;

import com.example.todo.domain.exception.InvalidTodoException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TodoTest {

    private static final Instant T1 = Instant.parse("2026-10-06T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-10-06T11:00:00Z");

    @Test
    void createTrimsTitleAndStartsPending() {
        Todo todo = Todo.create(7L, "  Buy milk ", null, T1);

        assertThat(todo.title()).isEqualTo("Buy milk");
        assertThat(todo.ownerId()).isEqualTo(7L);
        assertThat(todo.completed()).isFalse();
        assertThat(todo.id()).isNull();
    }

    @Test
    void createRequiresAnOwner() {
        assertThatThrownBy(() -> Todo.create(null, "Task", null, T1))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void createRejectsTooLongTitle() {
        String longTitle = "x".repeat(Todo.MAX_TITLE_LENGTH + 1);

        assertThatThrownBy(() -> Todo.create(1L, longTitle, null, T1))
                .isInstanceOf(InvalidTodoException.class);
    }

    @Test
    void completeReturnsNewInstanceAndKeepsOriginalUntouched() {
        Todo original = Todo.create(1L, "Task", null, T1);

        Todo done = original.complete(T2);

        assertThat(original.completed()).isFalse();
        assertThat(done.completed()).isTrue();
        assertThat(done.updatedAt()).isEqualTo(T2);
        assertThat(done.createdAt()).isEqualTo(T1);
        assertThat(done.ownerId()).isEqualTo(1L);
    }

    @Test
    void completeIsIdempotent() {
        Todo done = Todo.create(1L, "Task", null, T1).complete(T1);

        assertThat(done.complete(T2)).isSameAs(done);
    }
}
