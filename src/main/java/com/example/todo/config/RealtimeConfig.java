package com.example.todo.config;

import com.example.todo.adapter.in.realtime.RealtimeProperties;
import com.example.todo.adapter.in.realtime.RealtimeTicketStore;
import com.example.todo.application.service.RealtimeService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Nối các phần thời gian thực vào Spring: cấu hình app.realtime.*, sổ đăng ký client và kho ticket. */
@Configuration
@EnableConfigurationProperties(RealtimeProperties.class)
public class RealtimeConfig {

    /** Một bean, hai cổng vào: SubscribeToTodoEventsUseCase (SSE, WebSocket) và NotifyTodoEventUseCase (Kafka). */
    @Bean
    public RealtimeService realtimeService(RealtimeProperties properties) {
        return new RealtimeService(properties.maxConnectionsPerUser());
    }

    @Bean
    public RealtimeTicketStore realtimeTicketStore(Clock clock, RealtimeProperties properties) {
        return new RealtimeTicketStore(clock, properties.ticketTtl(), 10_000);
    }
}
