package com.example.todo.domain.model;

import com.example.todo.domain.exception.InvalidTodoException;

import java.time.Instant;
import java.util.Objects;

/**
 * Entity của DOMAIN (lõi nghiệp vụ). Không có annotation của Spring hay JPA.
 * Bất biến (immutable): mỗi thay đổi trả về một đối tượng Todo mới.
 *
 * ownerId: id của user sở hữu todo. Todo cũ tạo trước khi có đăng nhập có thể có ownerId = null,
 * khi đó chỉ ADMIN truy cập được. Todo tạo mới luôn có chủ.
 *
 * version: số phiên bản dùng để chống ghi đè đồng thời (optimistic locking).
 */
public final class Todo {

    public static final int MAX_TITLE_LENGTH = 100;
    public static final int MAX_DESCRIPTION_LENGTH = 500;

    private final Long id;
    private final Long ownerId;
    private final String title;
    private final String description;
    private final boolean completed;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Long version;

    private Todo(Long id, Long ownerId, String title, String description, boolean completed,
                 Instant createdAt, Instant updatedAt, Long version) {
        this.id = id;
        this.ownerId = ownerId;
        this.title = title;
        this.description = description;
        this.completed = completed;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    /** Tạo Todo mới cho một user (chưa có id và version, database sẽ cấp khi lưu). */
    public static Todo create(Long ownerId, String title, String description, Instant now) {
        Objects.requireNonNull(ownerId, "ownerId must not be null");
        Objects.requireNonNull(now, "now must not be null");
        return new Todo(null, ownerId, validTitle(title), validDescription(description),
                false, now, now, null);
    }

    /** Dựng lại Todo từ dữ liệu đã lưu (dùng bởi persistence adapter). */
    public static Todo reconstitute(Long id, Long ownerId, String title, String description,
                                    boolean completed, Instant createdAt, Instant updatedAt, Long version) {
        return new Todo(id, ownerId, title, description, completed, createdAt, updatedAt, version);
    }

    public Todo update(String newTitle, String newDescription, Instant now) {
        return new Todo(id, ownerId, validTitle(newTitle), validDescription(newDescription),
                completed, createdAt, now, version);
    }

    public Todo complete(Instant now) {
        if (completed) {
            return this;
        }
        return new Todo(id, ownerId, title, description, true, createdAt, now, version);
    }

    public Todo reopen(Instant now) {
        if (!completed) {
            return this;
        }
        return new Todo(id, ownerId, title, description, false, createdAt, now, version);
    }

    private static String validTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new InvalidTodoException("Title must not be blank");
        }
        String trimmed = title.trim();
        if (trimmed.length() > MAX_TITLE_LENGTH) {
            throw new InvalidTodoException("Title must not exceed " + MAX_TITLE_LENGTH + " characters");
        }
        return trimmed;
    }

    private static String validDescription(String description) {
        if (description == null) {
            return null;
        }
        String trimmed = description.trim();
        if (trimmed.length() > MAX_DESCRIPTION_LENGTH) {
            throw new InvalidTodoException(
                    "Description must not exceed " + MAX_DESCRIPTION_LENGTH + " characters");
        }
        return trimmed;
    }

    public Long id() { return id; }
    public Long ownerId() { return ownerId; }
    public String title() { return title; }
    public String description() { return description; }
    public boolean completed() { return completed; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public Long version() { return version; }
}
