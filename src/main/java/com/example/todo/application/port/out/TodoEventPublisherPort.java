package com.example.todo.application.port.out;

import com.example.todo.domain.event.TodoEvent;

/**
 * OUTBOUND PORT: báo ra thế giới bên ngoài rằng một todo vừa thay đổi.
 * Lõi không biết sự kiện đi đâu (Kafka, gọi thẳng trong bộ nhớ, hàng đợi khác).
 *
 * Được gọi BÊN TRONG giao dịch của use case. Bản hiện thực phải bảo đảm sự kiện chỉ được phát
 * khi giao dịch thành công (xem OutboxTodoEventPublisher và InProcessTodoEventPublisher).
 */
public interface TodoEventPublisherPort {

    void publish(TodoEvent event);
}
