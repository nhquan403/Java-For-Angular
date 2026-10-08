package com.example.todo.adapter.in.realtime;

import com.example.todo.adapter.in.web.AuthenticatedActor;
import com.example.todo.application.common.Actor;
import com.example.todo.application.common.TooManySubscriptionsException;
import com.example.todo.application.port.in.SubscribeToTodoEventsUseCase;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * INBOUND ADAPTER: đầu vào thời gian thực bằng SSE (Server-Sent Events) và cấp ticket.
 *
 * SSE là luồng một chiều server -> client trên HTTP thường, trình duyệt tự kết nối lại (EventSource).
 * Hợp với "thông báo cập nhật". Cần hai chiều (client gửi lên liên tục) thì dùng WebSocket.
 */
@RestController
public class RealtimeController {

    private final SubscribeToTodoEventsUseCase subscribe;
    private final RealtimeTicketStore tickets;
    private final RealtimeProperties properties;

    public RealtimeController(SubscribeToTodoEventsUseCase subscribe,
                              RealtimeTicketStore tickets,
                              RealtimeProperties properties) {
        this.subscribe = subscribe;
        this.tickets = tickets;
        this.properties = properties;
    }

    /** Phải đăng nhập bằng Bearer token. Trả về vé dùng một lần để mở SSE hoặc WebSocket. */
    @PostMapping(RealtimeEndpoints.TICKET)
    public TicketResponse ticket(Authentication authentication) {
        Actor actor = AuthenticatedActor.from(authentication);
        String ticket = tickets.issue(actor).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many pending tickets"));
        return new TicketResponse(ticket, properties.ticketTtl().toSeconds());
    }

    public record TicketResponse(String ticket, long expiresInSeconds) {
        /** Không in vé ra log. */
        @Override
        public String toString() {
            return "TicketResponse[expiresInSeconds=" + expiresInSeconds + "]";
        }
    }

    /**
     * Mở luồng sự kiện. Xác thực bằng header Authorization (client hỗ trợ header) hoặc ?ticket=... (EventSource).
     * Trả 429 (kèm Retry-After) nếu người dùng đã mở đủ số kết nối cho phép.
     */
    @GetMapping(path = RealtimeEndpoints.STREAM, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(Authentication authentication) {
        Actor actor = AuthenticatedActor.from(authentication);
        // Timeout 0 = không giới hạn ở tầng Spring, vì tuổi thọ do OutboundPump quản (maxConnectionAge).
        SseEmitter emitter = new SseEmitter(0L);

        OutboundPump pump = new OutboundPump(new SseChannel(emitter),
                properties.queueCapacity(), properties.heartbeatInterval(), properties.maxConnectionAge());
        SubscribeToTodoEventsUseCase.Subscription subscription;
        try {
            subscription = subscribe.subscribe(actor, pump::offer);
        } catch (TooManySubscriptionsException e) {
            // Trả thẳng 429 không kèm nội dung. Client SSE gửi "Accept: text/event-stream", nếu để
            // GlobalExceptionHandler trả lỗi dạng JSON thì Spring không chọn được định dạng và đổi thành 406.
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "5").build();
        }
        pump.onClose(subscription::close);

        // Khi client ngắt hoặc lỗi, Spring gọi các callback này. close() gọi nhiều lần vẫn an toàn.
        emitter.onCompletion(pump::close);
        emitter.onTimeout(pump::close);
        emitter.onError(e -> pump.close());

        // Gửi ngay một dòng chú thích để header 200 tới client lập tức. Không có nó, header chỉ được gửi
        // cùng lần ghi đầu tiên (heartbeat sau 25 giây), client phải chờ chừng đó mới biết đã kết nối.
        // Gửi TRƯỚC pump.start() để không bao giờ có hai luồng cùng ghi.
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            pump.close();
            return ResponseEntity.noContent().build();
        }
        pump.start();

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                // Báo nginx đừng gom đệm luồng, nếu không sự kiện bị giữ lại không tới client ngay.
                .header("X-Accel-Buffering", "no")
                .body(emitter);
    }

    private static final class SseChannel implements OutboundPump.Channel {

        private final SseEmitter emitter;

        private SseChannel(SseEmitter emitter) {
            this.emitter = emitter;
        }

        @Override
        public void send(String payload) throws IOException {
            emitter.send(SseEmitter.event().name("todo").data(payload, MediaType.TEXT_PLAIN));
        }

        /** Dòng bắt đầu bằng ":" là chú thích trong SSE: client bỏ qua nhưng kết nối được giữ sống. */
        @Override
        public void heartbeat() throws IOException {
            emitter.send(SseEmitter.event().comment("hb"));
        }

        @Override
        public void close() {
            emitter.complete();
        }
    }
}
