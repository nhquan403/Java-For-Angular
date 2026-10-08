package com.example.todo.config;

import com.example.todo.adapter.in.realtime.RealtimeEndpoints;
import com.example.todo.adapter.in.realtime.TodoWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/** Đăng ký đường WebSocket và giới hạn nguồn gốc (origin) được phép kết nối. */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final TodoWebSocketHandler handler;
    private final String[] allowedOrigins;

    public WebSocketConfig(TodoWebSocketHandler handler,
                           @Value("${app.cors.allowed-origins:}") String[] allowedOrigins) {
        this.handler = handler;
        this.allowedOrigins = allowedOrigins;
    }

    /**
     * WebSocket KHÔNG tuân theo CORS của trình duyệt: trang web bất kỳ đều mở được kết nối tới server của bạn,
     * kèm cookie nếu có (tấn công "cross-site WebSocket hijacking"). Vì vậy server phải tự kiểm tra header Origin.
     * Dùng chung danh sách app.cors.allowed-origins với CORS. Không khai báo gì thì chỉ cho cùng nguồn gốc.
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, RealtimeEndpoints.WEBSOCKET).setAllowedOrigins(allowedOrigins);
    }

    /** Server chỉ đẩy xuống, nên giới hạn tin nhắn client gửi lên thật nhỏ để chặn việc gửi dữ liệu rác. */
    @Bean
    public ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(1024);
        container.setMaxBinaryMessageBufferSize(1024);
        return container;
    }
}
