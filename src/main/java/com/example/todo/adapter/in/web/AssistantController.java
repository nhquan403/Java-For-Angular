package com.example.todo.adapter.in.web;

import com.example.todo.adapter.in.web.dto.AssistantChatRequest;
import com.example.todo.application.common.Actor;
import com.example.todo.application.common.AssistantEvent;
import com.example.todo.application.port.in.ChatWithAssistantUseCase;
import com.example.todo.application.port.in.ChatWithAssistantUseCase.Outcome;
import com.example.todo.config.AssistantProperties;
import com.example.todo.config.ConditionalOnAssistantEnabled;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * INBOUND ADAPTER: trợ lý AI dạng mini chat. Chỉ tồn tại khi trợ lý được bật (ConditionalOnAssistantEnabled),
 * nếu không thì đường dẫn trả 404 và frontend hiện "trợ lý chưa được bật".
 *
 * Luồng xử lý:
 * 1. Trên luồng request: đọc người dùng từ token (luồng khác không có SecurityContext), kiểm tra yêu cầu,
 *    giới hạn tần suất và gọi Claude vòng đầu. Lỗi ở đây thành HTTP 400 / 429 / 503 (GlobalExceptionHandler).
 * 2. Trả 200 text/event-stream rồi phát sự kiện trên luồng riêng. Lỗi từ đây thành sự kiện error.
 */
@RestController
@ConditionalOnAssistantEnabled
@RequestMapping("/api/assistant")
public class AssistantController {

    private static final Logger log = LoggerFactory.getLogger(AssistantController.class);

    private final ChatWithAssistantUseCase chat;
    private final ExecutorService executor;
    private final AssistantProperties properties;

    public AssistantController(ChatWithAssistantUseCase chat,
                               @Qualifier("assistantExecutor") ExecutorService executor,
                               AssistantProperties properties) {
        this.chat = chat;
        this.executor = executor;
        this.properties = properties;
    }

    @Operation(
            summary = "Hỏi trợ lý AI, nhận câu trả lời dạng Server-Sent Events",
            description = """
                    Response là text/event-stream. Mỗi sự kiện gồm dòng "event: <tên>", một dòng "data: <JSON>" \
                    và một dòng trống. Các sự kiện:
                    - delta {"text": string}: một phần câu trả lời, nối dần
                    - tool {"name": "list_todos" | "get_todo" | "suggest_todo"}: trợ lý đang dùng tool
                    - suggestion {"title": string, "description": string | null}: gợi ý todo để điền vào form tạo
                    - done {}: kết thúc
                    - error {"message": string}: lỗi sau khi đã bắt đầu trả lời (kết thúc luồng, không có done)

                    Tối đa 20 tin, mỗi tin tối đa 4000 ký tự, tin đầu và tin cuối là user. \
                    Trợ lý chỉ đọc todo với quyền của người gọi, không tạo, sửa hay xóa gì.""")
    @ApiResponse(responseCode = "200", description = "Luồng sự kiện SSE",
            content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE, schema = @Schema(type = "string")))
    @ApiResponse(responseCode = "400", description = "Yêu cầu không hợp lệ",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập hoặc token hết hạn",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
    @ApiResponse(responseCode = "429", description = "Hỏi quá số lần cho phép trong một phút",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
    @ApiResponse(responseCode = "503", description = "Không gọi được Claude",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
    @PostMapping(path = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<ResponseBodyEmitter> chat(Authentication authentication,
                                                    @RequestBody AssistantChatRequest request) {
        Actor actor = AuthenticatedActor.from(authentication);
        ChatWithAssistantUseCase.Reply reply = chat.start(request.toCommand(actor));

        ResponseBodyEmitter emitter = new ResponseBodyEmitter(properties.streamTimeout().toMillis());
        SseEventSink sink = new SseEventSink(emitter);
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        executor.execute(() -> deliver(actor, reply, sink, mdc));

        return ResponseEntity.ok()
                .contentType(SseEventSink.MEDIA_TYPE)
                .cacheControl(CacheControl.noStore())
                // Báo nginx đừng gom đệm luồng, nếu không sự kiện bị giữ lại không tới client ngay.
                .header("X-Accel-Buffering", "no")
                .body(emitter);
    }

    /** Chạy trên luồng riêng. Mang theo MDC để dòng log vẫn có request id. */
    private void deliver(Actor actor, ChatWithAssistantUseCase.Reply reply, SseEventSink sink,
                         Map<String, String> mdc) {
        if (mdc != null) {
            MDC.setContextMap(mdc);
        }
        try {
            Outcome outcome = reply.deliver(sink);
            // Chỉ ghi số liệu, không ghi nội dung hội thoại.
            log.info("Assistant reply: user={} status={} toolRounds={} inputTokens={} outputTokens={}",
                    actor.userId(), outcome.status(), outcome.toolRounds(),
                    outcome.inputTokens(), outcome.outputTokens());
        } catch (RuntimeException e) {
            log.error("Assistant reply failed: user={}", actor.userId(), e);
            sink.emit(new AssistantEvent.Error("Trợ lý đang gặp sự cố nên chưa trả lời xong. Bạn thử lại sau nhé."));
        } finally {
            sink.complete();
            MDC.clear();
        }
    }
}
