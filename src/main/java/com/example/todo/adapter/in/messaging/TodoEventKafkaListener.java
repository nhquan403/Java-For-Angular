package com.example.todo.adapter.in.messaging;

import com.example.todo.application.port.in.NotifyTodoEventUseCase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * INBOUND ADAPTER: "người gọi" ở đây là Kafka. Nhận sự kiện rồi chuyển cho use case giao cho client.
 *
 * groupId có số ngẫu nhiên: MỖI instance một consumer group riêng nên MỌI instance đều nhận MỌI sự kiện
 * (kiểu phát cho tất cả). Nếu dùng chung một group, Kafka chia sự kiện cho các instance, và client nối vào
 * instance không được chia sẽ không nhận được gì. Group tạm này không cần nhớ vị trí đọc, vì sự kiện cũ
 * không còn ý nghĩa với client mới kết nối (auto-offset-reset=latest).
 */
@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
class TodoEventKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(TodoEventKafkaListener.class);

    private final NotifyTodoEventUseCase notifier;

    TodoEventKafkaListener(NotifyTodoEventUseCase notifier) {
        this.notifier = notifier;
    }

    @KafkaListener(
            topics = "${app.kafka.topic}",
            groupId = "${app.kafka.consumer-group-prefix:todo-api-realtime}-#{T(java.util.UUID).randomUUID()}")
    void onMessage(ConsumerRecord<String, String> record) {
        Long ownerId = parseOwner(record.key());
        int delivered = notifier.notifyEvent(ownerId, record.value());
        if (log.isTraceEnabled()) {
            log.trace("Event offset={} delivered to {} clients", record.offset(), delivered);
        }
    }

    /** Khóa "none" nghĩa là todo cũ chưa có chủ. Khóa hỏng thì coi như không có chủ (chỉ ADMIN nhận). */
    private static Long parseOwner(String key) {
        if (key == null || key.equals("none")) {
            return null;
        }
        try {
            return Long.valueOf(key);
        } catch (NumberFormatException e) {
            log.warn("Ignoring unparseable owner key '{}'", key);
            return null;
        }
    }
}
