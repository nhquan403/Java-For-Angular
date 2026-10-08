package com.example.todo.application.port.out;

import com.example.todo.application.port.in.ChatWithAssistantUseCase.ChatMessage;

import java.util.List;
import java.util.Map;

/**
 * OUTBOUND PORT: nói chuyện với mô hình AI (hiện thực: adapter.out.anthropic, gọi Claude).
 *
 * Một Session giữ lịch sử của MỘT câu hỏi: adapter tự lưu nguyên câu trả lời của mô hình (kể cả các khối
 * thinking mà lõi không cần biết) để gửi lại ở vòng sau. Lõi chỉ thấy văn bản, lời gọi tool và kết quả tool.
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
         * Gửi lịch sử hiện tại cho mô hình, ghi nhận câu trả lời vào lịch sử rồi trả về.
         *
         * @throws com.example.todo.application.common.AssistantModelException gọi mô hình thất bại
         */
        ModelTurn next();

        /** Thêm kết quả của MỌI lời gọi tool ở vòng vừa rồi, trong một tin duy nhất. */
        void addToolResults(List<ToolResult> results);
    }

    /**
     * @param inputSchema JSON Schema của tham số (type object, properties, required, additionalProperties)
     */
    record ToolSpec(String name, String description, Map<String, Object> inputSchema) {
    }

    record ModelTurn(List<Block> blocks, StopReason stopReason, long inputTokens, long outputTokens) {
        public ModelTurn {
            blocks = List.copyOf(blocks);
        }
    }

    sealed interface Block {
    }

    record Text(String text) implements Block {
    }

    /** @param input tham số mô hình gửi: Map/List/String/Number/Boolean/null */
    record ToolCall(String id, String name, Map<String, Object> input) implements Block {
    }

    enum StopReason { END_TURN, TOOL_USE, MAX_TOKENS, REFUSAL, OTHER }

    /** @param content nội dung trả cho mô hình (JSON), error = true nếu tool thất bại */
    record ToolResult(String toolCallId, String content, boolean error) {
    }
}
