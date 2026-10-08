package com.example.todo.adapter.in.web;

import com.example.todo.application.common.AssistantEvent;
import com.example.todo.application.common.JsonText;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Định dạng sự kiện trợ lý theo Server-Sent Events, đúng hợp đồng với frontend:
 * dòng "event: tên", một dòng "data: JSON", rồi một dòng trống.
 * JSON không bao giờ chứa ký tự xuống dòng thật (đã thoát thành \n) nên luôn nằm gọn trên một dòng data.
 */
final class AssistantSseFormat {

    private AssistantSseFormat() {
    }

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
