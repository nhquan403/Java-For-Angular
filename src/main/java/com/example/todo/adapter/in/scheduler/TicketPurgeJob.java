package com.example.todo.adapter.in.scheduler;

import com.example.todo.adapter.in.realtime.RealtimeTicketStore;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Mỗi phút xóa các ticket thời gian thực đã hết hạn mà chưa ai dùng. */
@Component
class TicketPurgeJob {

    private final RealtimeTicketStore tickets;

    TicketPurgeJob(RealtimeTicketStore tickets) {
        this.tickets = tickets;
    }

    @Scheduled(fixedDelay = 60_000)
    void run() {
        tickets.purgeExpired();
    }
}
