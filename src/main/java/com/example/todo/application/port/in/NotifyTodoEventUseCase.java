package com.example.todo.application.port.in;

/**
 * INBOUND PORT: "có một sự kiện todo vừa tới, hãy chuyển cho những client đang kết nối".
 * Người gọi là Kafka listener (hoặc bản phát trong bộ nhớ khi tắt Kafka).
 */
public interface NotifyTodoEventUseCase {

    /**
     * @param ownerId chủ của todo, null nếu là todo cũ chưa có chủ (chỉ ADMIN nhận)
     * @param payload nội dung gửi nguyên văn cho client (JSON)
     * @return số client đã được giao
     */
    int notifyEvent(Long ownerId, String payload);
}
