package com.example.todo.adapter.out.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Một dòng trong "hộp thư đi" (outbox): sự kiện đã ghi cùng giao dịch với todo, đang chờ đẩy sang Kafka. */
@Entity
@Table(name = "outbox_events")
public class OutboxEventJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long aggregateId;

    /** Dùng làm khóa của bản ghi Kafka, để mọi sự kiện của một người nằm cùng partition và giữ đúng thứ tự. */
    @Column
    private Long ownerId;

    @Column(nullable = false, length = 40)
    private String eventType;

    @Column(nullable = false, length = 2000)
    private String payload;

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant publishedAt;

    protected OutboxEventJpaEntity() {
    }

    public OutboxEventJpaEntity(Long aggregateId, Long ownerId, String eventType, String payload, Instant createdAt) {
        this.aggregateId = aggregateId;
        this.ownerId = ownerId;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    /** Entity đang được quản lý trong giao dịch, nên Hibernate tự ghi thay đổi này khi commit. */
    public void markPublished(Instant at) {
        this.publishedAt = at;
    }

    public Long getId() { return id; }
    public Long getAggregateId() { return aggregateId; }
    public Long getOwnerId() { return ownerId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
}
