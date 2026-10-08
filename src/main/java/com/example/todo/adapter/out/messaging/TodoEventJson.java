package com.example.todo.adapter.out.messaging;

import com.example.todo.domain.event.TodoEvent;

/**
 * Định dạng JSON của sự kiện gửi cho client (qua Kafka, SSE và WebSocket đều dùng chung một chuỗi này).
 *
 * Viết tay thay vì dùng thư viện JSON: chỉ có 6 trường cố định, cần tuần tự hóa nhanh, và không muốn phụ thuộc
 * vào việc Jackson bị đổi phiên bản. Phần dễ sai nhất là thoát ký tự trong title, đã có test riêng.
 */
public final class TodoEventJson {

    private TodoEventJson() {
    }

    public static String toJson(TodoEvent event) {
        StringBuilder sb = new StringBuilder(160);
        sb.append("{\"type\":\"").append(event.type().name()).append('"')
                .append(",\"todoId\":").append(event.todoId())
                .append(",\"ownerId\":").append(event.ownerId() == null ? "null" : event.ownerId().toString())
                .append(",\"title\":");
        appendString(sb, event.title());
        sb.append(",\"completed\":").append(event.completed())
                .append(",\"occurredAt\":\"").append(event.occurredAt()).append("\"}");
        return sb.toString();
    }

    private static void appendString(StringBuilder sb, String value) {
        if (value == null) {
            sb.append("null");
            return;
        }
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    // Ký tự điều khiển bắt buộc phải thoát. U+2028 và U+2029 hợp lệ trong JSON nhưng làm hỏng
                    // JavaScript cũ và vài bộ phân tích dòng, nên thoát luôn cho an toàn.
                    if (c < 0x20 || c == ' ' || c == ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
