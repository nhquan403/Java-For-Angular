package com.example.todo.application.common;

import java.util.Collection;
import java.util.Map;

/**
 * Viết JSON từ các giá trị Java đơn giản: Map (khóa chuỗi), Collection, String, Number, Boolean, null.
 * Giá trị khác (ví dụ Instant) được ghi bằng toString() dưới dạng chuỗi.
 *
 * Viết tay để lõi ứng dụng không phụ thuộc thư viện JSON (giống TodoEventJson). Dùng cho kết quả tool,
 * ngữ cảnh trang trong system prompt và dữ liệu sự kiện SSE của trợ lý.
 */
public final class JsonText {

    private JsonText() {
    }

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder(128);
        append(sb, value);
        return sb.toString();
    }

    private static void append(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Boolean || value instanceof Integer || value instanceof Long) {
            sb.append(value);
        } else if (value instanceof Number number) {
            double d = number.doubleValue();
            sb.append(Double.isFinite(d) ? number.toString() : "null");
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                appendString(sb, String.valueOf(entry.getKey()));
                sb.append(':');
                append(sb, entry.getValue());
            }
            sb.append('}');
        } else if (value instanceof Collection<?> collection) {
            sb.append('[');
            boolean first = true;
            for (Object item : collection) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                append(sb, item);
            }
            sb.append(']');
        } else {
            appendString(sb, value.toString());
        }
    }

    private static void appendString(StringBuilder sb, String value) {
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
                    // Ký tự điều khiển bắt buộc phải thoát; U+2028/U+2029 thoát luôn cho JavaScript cũ.
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
