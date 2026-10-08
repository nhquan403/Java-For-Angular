package com.example.todo.application.common;

/**
 * Sự kiện trợ lý AI gửi về client trong lúc trả lời một câu hỏi.
 * Tên và dữ liệu của từng loại là hợp đồng cố định với frontend (xem AssistantController).
 */
public sealed interface AssistantEvent {

    /** Một phần văn bản trả lời, client nối dần. */
    record Delta(String text) implements AssistantEvent {
    }

    /** Trợ lý đang dùng một tool (list_todos, get_todo, suggest_todo). */
    record ToolUse(String name) implements AssistantEvent {
    }

    /** Gợi ý một todo để người dùng tự điền vào form tạo. Không ghi gì vào database. */
    record Suggestion(String title, String description) implements AssistantEvent {
    }

    /** Trả lời xong. */
    record Done() implements AssistantEvent {
    }

    /** Lỗi xảy ra sau khi đã bắt đầu trả lời. Thông báo bằng tiếng Việt, hiện thẳng cho người dùng. */
    record Error(String message) implements AssistantEvent {
    }
}
