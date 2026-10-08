package com.example.todo.application.port.in;

import com.example.todo.application.common.Actor;

import java.util.function.Consumer;

/** INBOUND PORT: một client (SSE hoặc WebSocket) đăng ký nhận sự kiện của mình. */
public interface SubscribeToTodoEventsUseCase {

    /**
     * USER chỉ nhận sự kiện todo của mình, ADMIN nhận tất cả.
     *
     * @param sink nơi nhận nội dung sự kiện. PHẢI không chặn (chỉ đưa vào hàng đợi), vì nó được gọi từ
     *             luồng xử lý chung. Nếu sink ném exception, đăng ký bị hủy.
     * @throws com.example.todo.application.common.TooManySubscriptionsException quá số kết nối cho phép
     */
    Subscription subscribe(Actor actor, Consumer<String> sink);

    /** Gọi close() khi client ngắt kết nối. Gọi nhiều lần không sao. */
    interface Subscription extends AutoCloseable {
        @Override
        void close();
    }
}
