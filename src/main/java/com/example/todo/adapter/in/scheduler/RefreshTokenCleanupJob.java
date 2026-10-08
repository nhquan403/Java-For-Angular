package com.example.todo.adapter.in.scheduler;

import com.example.todo.application.port.in.PurgeExpiredTokensUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * INBOUND ADAPTER: "người gọi" ở đây là đồng hồ, không phải HTTP. Mỗi giờ gọi use case dọn
 * refresh token đã hết hạn. Token đã thu hồi nhưng chưa hết hạn vẫn được giữ lại, vì cần chúng
 * để phát hiện việc dùng lại token bị đánh cắp.
 */
@Component
class RefreshTokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupJob.class);

    private final PurgeExpiredTokensUseCase purge;

    RefreshTokenCleanupJob(PurgeExpiredTokensUseCase purge) {
        this.purge = purge;
    }

    @Scheduled(cron = "0 0 * * * *")
    void run() {
        int removed = purge.purgeExpired();
        if (removed > 0) {
            log.info("Purged {} expired refresh tokens", removed);
        }
    }
}
