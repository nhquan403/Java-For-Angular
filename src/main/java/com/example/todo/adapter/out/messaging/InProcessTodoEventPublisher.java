package com.example.todo.adapter.out.messaging;

import com.example.todo.application.port.in.NotifyTodoEventUseCase;
import com.example.todo.application.port.out.TodoEventPublisherPort;
import com.example.todo.domain.event.TodoEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * OUTBOUND ADAPTER dùng khi TẮT Kafka (app.kafka.enabled=false, mặc định ở dev): giao sự kiện thẳng cho
 * client đang nối vào chính instance này, không qua broker. Chỉ đủ cho MỘT instance.
 *
 * Chỉ giao SAU KHI giao dịch commit. Nếu giao ngay trong giao dịch rồi sau đó rollback, client sẽ nhận
 * một sự kiện về thay đổi chưa từng xảy ra.
 */
@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "false", matchIfMissing = true)
class InProcessTodoEventPublisher implements TodoEventPublisherPort {

    private final NotifyTodoEventUseCase notifier;

    InProcessTodoEventPublisher(NotifyTodoEventUseCase notifier) {
        this.notifier = notifier;
    }

    @Override
    public void publish(TodoEvent event) {
        String payload = TodoEventJson.toJson(event);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            notifier.notifyEvent(event.ownerId(), payload);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notifier.notifyEvent(event.ownerId(), payload);
            }
        });
    }
}
