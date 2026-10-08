package com.example.todo.adapter.out.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Đọc outbox rồi đẩy sang Kafka (mẫu "transactional outbox"). Đảm bảo "ít nhất một lần": nếu app chết giữa
 * lúc đã gửi Kafka và chưa kịp đánh dấu, sự kiện được gửi lại lần sau, nên consumer phải chịu được trùng lặp
 * (với thông báo thời gian thực thì trùng không nguy hiểm).
 *
 * Tối ưu:
 * - Gửi cả lô không đồng bộ rồi mới chờ kết quả (không chờ từng bản ghi), Kafka gộp thành vài request.
 * - Chỉ một instance chạy tại một thời điểm (advisory lock), thứ tự theo id được giữ.
 * - Gặp lỗi ở bản ghi nào thì dừng ở đó, chỉ đánh dấu phần đã gửi xong, giữ đúng thứ tự khi thử lại.
 */
@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    /** Số tùy ý nhưng cố định, mọi instance của ứng dụng này phải dùng chung. */
    private static final long ADVISORY_LOCK_KEY = 7_001_001L;

    private final SpringDataOutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final String topic;
    private final int batchSize;
    private final long sendTimeoutMs;
    private final Duration retention;

    OutboxRelay(SpringDataOutboxRepository outbox,
                KafkaTemplate<String, String> kafka,
                TransactionTemplate tx,
                Clock clock,
                @Value("${app.kafka.topic}") String topic,
                @Value("${app.kafka.outbox.batch-size:200}") int batchSize,
                @Value("${app.kafka.outbox.send-timeout-ms:10000}") long sendTimeoutMs,
                @Value("${app.kafka.outbox.retention:1d}") Duration retention) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.tx = tx;
        this.clock = clock;
        this.topic = topic;
        this.batchSize = batchSize;
        this.sendTimeoutMs = sendTimeoutMs;
        this.retention = retention;
    }

    @Scheduled(fixedDelayString = "${app.kafka.outbox.poll-interval-ms:200}")
    void relay() {
        try {
            Integer sent = tx.execute(status -> relayBatch());
            if (sent != null && sent > 0) {
                log.debug("Relayed {} outbox events to Kafka", sent);
            }
        } catch (RuntimeException e) {
            // Không ném tiếp: lỗi tạm thời (Kafka chưa sẵn sàng...) thì tick sau thử lại.
            log.warn("Outbox relay failed, will retry: {}", e.toString());
        }
    }

    private int relayBatch() {
        if (!outbox.tryAdvisoryXactLock(ADVISORY_LOCK_KEY)) {
            return 0; // instance khác đang làm
        }
        List<OutboxEventJpaEntity> batch = outbox.findByPublishedAtIsNullOrderByIdAsc(PageRequest.of(0, batchSize));
        if (batch.isEmpty()) {
            return 0;
        }

        List<CompletableFuture<?>> futures = new ArrayList<>(batch.size());
        for (OutboxEventJpaEntity event : batch) {
            futures.add(kafka.send(topic, key(event), event.getPayload()));
        }

        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(sendTimeoutMs);
        int published = 0;
        for (int i = 0; i < batch.size(); i++) {
            try {
                futures.get(i).get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                log.warn("Kafka did not confirm outbox event {}, stopping this batch: {}",
                        batch.get(i).getId(), e.toString());
                break;
            }
            batch.get(i).markPublished(clock.instant());
            published++;
        }
        return published;
    }

    private static String key(OutboxEventJpaEntity event) {
        return event.getOwnerId() == null ? "none" : event.getOwnerId().toString();
    }

    /** Xóa các dòng đã đẩy xong và đã quá hạn giữ, để bảng không phình mãi. */
    @Scheduled(cron = "0 15 * * * *")
    void purgePublished() {
        int removed = outbox.deletePublishedBefore(clock.instant().minus(retention));
        if (removed > 0) {
            log.info("Purged {} published outbox events", removed);
        }
    }
}
