package com.example.todo.adapter.in.realtime;

import com.example.todo.adapter.in.web.AuthenticatedActor;
import com.example.todo.application.common.Actor;
import com.example.todo.application.common.TooManySubscriptionsException;
import com.example.todo.application.port.in.SubscribeToTodoEventsUseCase;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * INBOUND ADAPTER: đầu vào thời gian thực bằng WebSocket tại /ws/todos.
 *
 * Ở đây chỉ dùng để server đẩy sự kiện xuống (cùng nội dung với SSE), tin nhắn client gửi lên bị bỏ qua.
 * Dùng WebSocket thay SSE khi sau này cần client gửi lệnh lên qua cùng kết nối.
 *
 * Danh tính lấy từ bước bắt tay HTTP đã qua Spring Security (Bearer hoặc ?ticket=...), xem TicketAuthenticationFilter.
 */
@Component
public class TodoWebSocketHandler extends TextWebSocketHandler {

    private final SubscribeToTodoEventsUseCase subscribe;
    private final RealtimeProperties properties;
    private final ConcurrentHashMap<String, OutboundPump> pumps = new ConcurrentHashMap<>();

    public TodoWebSocketHandler(SubscribeToTodoEventsUseCase subscribe, RealtimeProperties properties) {
        this.subscribe = subscribe;
        this.properties = properties;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        if (!(session.getPrincipal() instanceof Authentication authentication)) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Not authenticated"));
            return;
        }
        Actor actor = AuthenticatedActor.from(authentication);

        OutboundPump pump = new OutboundPump(new WebSocketChannel(session),
                properties.queueCapacity(), properties.heartbeatInterval(), properties.maxConnectionAge());
        String id = session.getId();
        pumps.put(id, pump);
        SubscribeToTodoEventsUseCase.Subscription subscription;
        try {
            subscription = subscribe.subscribe(actor, pump::offer);
        } catch (TooManySubscriptionsException e) {
            pumps.remove(id);
            session.close(CloseStatus.SERVICE_OVERLOAD.withReason("Too many connections"));
            return;
        }
        pump.onClose(() -> {
            pumps.remove(id);
            subscription.close();
        });
        pump.start();
    }

    /** Server chỉ đẩy xuống, tin nhắn từ client bị bỏ qua. */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        close(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        close(session);
    }

    private void close(WebSocketSession session) {
        OutboundPump pump = pumps.get(session.getId());
        if (pump != null) {
            pump.close();
        }
    }

    private static final class WebSocketChannel implements OutboundPump.Channel {

        private final WebSocketSession session;

        private WebSocketChannel(WebSocketSession session) {
            this.session = session;
        }

        /** Chỉ luồng ghi của OutboundPump gọi hàm này nên không bao giờ có hai lần ghi đồng thời. */
        @Override
        public void send(String payload) throws IOException {
            session.sendMessage(new TextMessage(payload));
        }

        /** Khung ping của giao thức WebSocket: trình duyệt tự trả pong, không cần code ở client. */
        @Override
        public void heartbeat() throws IOException {
            session.sendMessage(new PingMessage());
        }

        @Override
        public void close() {
            try {
                session.close(CloseStatus.NORMAL);
            } catch (IOException ignored) {
                // đã đóng rồi
            }
        }
    }
}
