package com.example.todo.adapter.in.web;

import com.example.todo.application.common.AssistantEvent;
import com.example.todo.application.common.JsonText;
import com.example.todo.application.port.in.ChatWithAssistantUseCase;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ghi sự kiện của trợ lý ra kết nối theo định dạng Server-Sent Events, đúng hợp đồng với frontend:
 * dòng "event: tên", một dòng "data: JSON", rồi một dòng trống.
 *
 * Ghi thất bại (client bấm Dừng, mất mạng, hết thời gian) nghĩa là client đã đi: emit trả false để use case
 * dừng gọi Claude ngay, không tốn thêm token.
 */
final class SseEventSink implements ChatWithAssistantUseCase.AssistantEventSink {

    static final MediaType MEDIA_TYPE = new MediaType("text", "event-stream", StandardCharsets.UTF_8);

    private final ResponseBodyEmitter emitter;
    private volatile boolean closed;

    SseEventSink(ResponseBodyEmitter emitter) {
        this.emitter = emitter;
        emitter.onCompletion(() -> closed = true);
        emitter.onTimeout(() -> closed = true);
        emitter.onError(e -> closed = true);
    }

    @Override
    public boolean emit(AssistantEvent event) {
        if (closed) {
            return false;
        }
        try {
            emitter.send(format(event), MEDIA_TYPE);
            return true;
        } catch (IOException | IllegalStateException e) {
            closed = true;
            return false;
        }
    }

    void complete() {
        if (!closed) {
            closed = true;
            emitter.complete();
        }
    }

    /** JSON không bao giờ chứa ký tự xuống dòng thật (đã thoát thành \n) nên luôn nằm gọn trên một dòng data. */
    static String format(AssistantEvent event) {
        return switch (event) {
            case AssistantEvent.Delta delta -> frame("delta", Map.of("text", delta.text()));
            case AssistantEvent.ToolUse tool -> frame("tool", Map.of("name", tool.name()));
            case AssistantEvent.Suggestion suggestion -> {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("title", suggestion.title());
                data.put("description", suggestion.description());
                yield frame("suggestion", data);
            }
            case AssistantEvent.Done done -> frame("done", Map.of());
            case AssistantEvent.Error error -> frame("error", Map.of("message", error.message()));
        };
    }

    private static String frame(String name, Map<String, ?> data) {
        return "event: " + name + "\ndata: " + JsonText.write(data) + "\n\n";
    }
}
