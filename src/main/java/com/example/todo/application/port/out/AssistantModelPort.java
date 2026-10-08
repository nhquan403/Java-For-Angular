package com.example.todo.application.port.out;

import com.example.todo.application.port.in.ChatWithAssistantUseCase.ChatMessage;

import java.util.List;
import java.util.Map;

/**
 * OUTBOUND PORT: nói chuyện với mô hình AI (hiện thực: adapter.out.anthropic, gọi Claude).
 *
 * Một Session giữ lịch sử của MỘT câu hỏi: adapter tự lưu nguyên câu trả lời của mô hình (kể cả các khối
 * thinking mà lõi không cần biết) để gửi lại ở vòng sau. Lõi chỉ thấy văn bản, lời gọi tool và kết quả tool.
 *
 * Mỗi vòng trả lời chạy hai bước: next() gửi request (lỗi như key sai, quá tải ném ngay ở đây), rồi
 * TurnStream.read() nhận câu trả lời dần dần, từng mẩu văn bản một, để client thấy chữ hiện ra từ từ.
 */
public interface AssistantModelPort {

    /**
     * @param systemPrompt chỉ dẫn hệ thống, đã gồm ngữ cảnh trang
     * @param tools        các tool mô hình được gọi
     * @param history      hội thoại do client gửi, tin cuối là của user
     */
    Session open(String systemPrompt, List<ToolSpec> tools, List<ChatMessage> history);

    interface Session {

        /**
         * Gửi lịch sử hiện tại cho mô hình và mở luồng trả lời.
         *
         * @throws com.example.todo.application.common.AssistantModelException gọi mô hình thất bại
         */
        TurnStream next();

        /** Thêm kết quả của MỌI lời gọi tool ở vòng vừa rồi, trong một tin duy nhất. */
        void addToolResults(List<ToolResult> results);
    }

    interface TurnStream {

        /**
         * Đọc hết câu trả lời của vòng này. Mỗi mẩu văn bản được đưa ngay cho listener; listener trả false
         * (client đã ngắt) thì luồng bị đóng ngay và kết quả có stopReason CANCELLED.
         * Câu trả lời đầy đủ được ghi vào lịch sử của Session.
         *
         * @throws com.example.todo.application.common.AssistantModelException luồng hỏng giữa chừng
         */
        ModelTurn read(TextListener listener);
    }

    @FunctionalInterface
    interface TextListener {
        /** @return false để dừng nhận (client đã ngắt kết nối) */
        boolean onText(String text);
    }

    /**
     * @param inputSchema JSON Schema của tham số (type object, properties, required, additionalProperties)
     */
    record ToolSpec(String name, String description, Map<String, Object> inputSchema) {
    }

    /** Kết thúc một vòng trả lời. Văn bản đã được phát qua TextListener nên không nằm ở đây. */
    record ModelTurn(List<ToolCall> toolCalls, StopReason stopReason, long inputTokens, long outputTokens) {
        public ModelTurn {
            toolCalls = List.copyOf(toolCalls);
        }

        public static ModelTurn cancelled() {
            return new ModelTurn(List.of(), StopReason.CANCELLED, 0, 0);
        }
    }

    /** @param input tham số mô hình gửi: Map/List/String/Number/Boolean/null */
    record ToolCall(String id, String name, Map<String, Object> input) {
    }

    enum StopReason { END_TURN, TOOL_USE, MAX_TOKENS, REFUSAL, CANCELLED, OTHER }

    /** @param content nội dung trả cho mô hình (JSON), error = true nếu tool thất bại */
    record ToolResult(String toolCallId, String content, boolean error) {
    }
}
