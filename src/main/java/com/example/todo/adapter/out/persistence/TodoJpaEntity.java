package com.example.todo.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * Entity của JPA: chỉ phục vụ việc lưu database, nằm trong adapter.
 * Tách riêng với domain Todo để domain không bị dính JPA.
 *
 * Tên bảng và cột phải khớp các file migration trong db/migration
 * (Hibernate chỉ kiểm tra, không tự tạo bảng).
 */
@Entity
@Table(name = "todos")
public class TodoJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Cho phép null cho todo cũ chưa có chủ (xem V3__add_todo_owner.sql). */
    @Column
    private Long ownerId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private boolean completed;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /**
     * Hibernate tự tăng mỗi lần UPDATE và thêm "WHERE version = ?" vào câu lệnh.
     * Nếu ai đó đã sửa trước, câu UPDATE không khớp dòng nào và Hibernate báo xung đột.
     */
    @Version
    private Long version;

    /** JPA bắt buộc có constructor không tham số. */
    protected TodoJpaEntity() {
    }

    public TodoJpaEntity(Long id, Long ownerId, String title, String description, boolean completed,
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

    public Long getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public boolean isCompleted() { return completed; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
