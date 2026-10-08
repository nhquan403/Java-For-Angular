package com.example.todo.adapter.in.realtime;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * "Bơm" gửi dữ liệu cho MỘT client (SSE hoặc WebSocket) mà không làm chậm ai khác.
 *
 * Vấn đề: sự kiện được giao từ luồng của Kafka (hoặc luồng của request). Nếu gửi thẳng xuống mạng ở luồng đó,
 * một client mạng chậm sẽ làm tắc cả luồng, mọi client khác phải chờ theo.
 *
 * Cách làm: offer() chỉ nhét vào hàng đợi có giới hạn rồi quay lại ngay. Một luồng ảo (virtual thread)
 * riêng của client này lấy ra và ghi xuống mạng. Hàng đợi đầy nghĩa là client không theo kịp, ngắt nó
 * (client tự kết nối lại và tải lại dữ liệu), thay vì để bộ nhớ phình ra.
 *
 * Luồng ghi cũng lo ba việc định kỳ: gửi heartbeat khi im lặng, đóng khi quá tuổi tối đa, dọn dẹp khi lỗi.
 */
final class OutboundPump {

    /** Cách ghi xuống kênh cụ thể. Chỉ luồng ghi của bơm gọi các hàm này, nên không cần lo đồng thời. */
    interface Channel {
        void send(String payload) throws Exception;

        void heartbeat() throws Exception;

        void close();
    }

    private final Channel channel;
    private final BlockingQueue<String> queue;
    private final long heartbeatNanos;
    private final long deadlineNanos;
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile Runnable onClose = () -> { };
    private volatile Thread writer;

    OutboundPump(Channel channel, int queueCapacity, java.time.Duration heartbeat, java.time.Duration maxAge) {
        this.channel = channel;
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.heartbeatNanos = heartbeat.toNanos();
        this.deadlineNanos = System.nanoTime() + maxAge.toNanos();
    }

    /**
     * Việc cần làm khi bơm đóng (ví dụ hủy đăng ký). Nếu bơm đã đóng trước khi kịp đặt thì chạy ngay.
     * Hành động phải chịu được việc chạy hai lần.
     */
    void onClose(Runnable action) {
        this.onClose = action;
        if (closed.get()) {
            action.run();
        }
    }

    void start() {
        // Luồng ảo rẻ, một client một luồng không đáng kể. Lưu ý với JDK 21: ghi mạng bên trong khối
        // synchronized của Tomcat có thể "ghim" luồng nền; từ JDK 24 hết vấn đề này.
        writer = Thread.ofVirtual().name("realtime-writer").start(this::run);
    }

    /**
     * Không chặn. Được gọi như "sink" của RealtimeService.
     *
     * @throws IllegalStateException nếu đã đóng hoặc client quá chậm, để RealtimeService hủy đăng ký
     */
    void offer(String payload) {
        if (closed.get()) {
            throw new IllegalStateException("connection closed");
        }
        if (!queue.offer(payload)) {
            close();
            throw new IllegalStateException("slow consumer, queue is full");
        }
    }

    void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            onClose.run();
        } finally {
            try {
                channel.close();
            } catch (RuntimeException ignored) {
                // đóng một kết nối đã chết thì lỗi cũng không sao
            }
            Thread w = writer;
            if (w != null && w != Thread.currentThread()) {
                w.interrupt();
            }
        }
    }

    boolean isClosed() {
        return closed.get();
    }

    private void run() {
        try {
            while (!closed.get()) {
                long remaining = deadlineNanos - System.nanoTime();
                if (remaining <= 0) {
                    break; // hết tuổi thọ, đóng để client kết nối lại với ticket mới
                }
                String message = queue.poll(Math.min(heartbeatNanos, remaining), TimeUnit.NANOSECONDS);
                if (message != null) {
                    channel.send(message);
                } else if (System.nanoTime() < deadlineNanos) {
                    channel.heartbeat();
                }
            }
        } catch (InterruptedException e) {
            // close() đánh thức luồng, thoát êm
        } catch (Exception e) {
            // client đã ngắt hoặc ghi lỗi
        } finally {
            close();
        }
    }
}
