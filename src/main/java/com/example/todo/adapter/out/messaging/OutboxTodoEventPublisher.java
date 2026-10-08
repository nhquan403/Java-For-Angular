package com.example.todo.adapter.out.messaging;

import com.example.todo.application.port.out.TodoEventPublisherPort;
import com.example.todo.domain.event.TodoEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * OUTBOUND ADAPTER dùng khi BẬT Kafka: ghi sự kiện vào bảng outbox_events CÙNG GIAO DỊCH với todo.
 *
 * Vì sao không gọi thẳng KafkaTemplate.send ở đây? Database và Kafka là hai hệ thống, không có giao dịch chung.
 * Gửi Kafka rồi mà database rollback thì có sự kiện ma. Database commit rồi mà Kafka lỗi thì mất sự kiện.
 * Ghi vào outbox cùng giao dịch thì hoặc có cả todo lẫn sự kiện, hoặc không có gì. OutboxRelay lo phần đẩy sang Kafka.
 */
@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
class OutboxTodoEventPublisher implements TodoEventPublisherPort {

    private final SpringDataOutboxRepository outbox;

    OutboxTodoEventPublisher(SpringDataOutboxRepository outbox) {
        this.outbox = outbox;
    }

    /** MANDATORY: bắt buộc đang ở trong giao dịch của use case, gọi ngoài giao dịch sẽ báo lỗi ngay. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(TodoEvent event) {
        outbox.save(new OutboxEventJpaEntity(
                event.todoId(), event.ownerId(), event.type().name(),
                TodoEventJson.toJson(event), event.occurredAt()));
    }
}
