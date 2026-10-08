package com.example.todo.application.service;

import com.example.todo.application.common.AssistantModelException;
import com.example.todo.application.port.in.ChatWithAssistantUseCase.ChatMessage;
import com.example.todo.application.port.out.AssistantModelPort;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Mô hình AI giả (fake) cho test: trả lần lượt các vòng đã soạn sẵn, ghi lại mọi thứ lõi gửi cho nó.
 * Không gọi Claude thật.
 */
public class ScriptedAssistantModel implements AssistantModelPort {

    private final Deque<Supplier<ModelTurn>> script = new ArrayDeque<>();
    private Supplier<ModelTurn> whenScriptEnds;

    public String systemPrompt;
    public List<ToolSpec> tools;
    public List<ChatMessage> history;
    public int calls;
    /** Mỗi phần tử là kết quả tool của một vòng (gửi trong một tin). */
    public final List<List<ToolResult>> toolResultBatches = new ArrayList<>();

    public ScriptedAssistantModel then(ModelTurn turn) {
        script.add(() -> turn);
        return this;
    }

    public ScriptedAssistantModel thenFail() {
        script.add(() -> {
            throw new AssistantModelException("simulated failure", null);
        });
        return this;
    }

    /** Hết kịch bản thì luôn trả vòng này (ví dụ mô hình gọi tool mãi không dừng). */
    public ScriptedAssistantModel forever(ModelTurn turn) {
        whenScriptEnds = () -> turn;
        return this;
    }

    @Override
    public Session open(String systemPrompt, List<ToolSpec> tools, List<ChatMessage> history) {
        this.systemPrompt = systemPrompt;
        this.tools = tools;
        this.history = history;
        return new Session() {
            @Override
            public ModelTurn next() {
                calls++;
                Supplier<ModelTurn> step = script.isEmpty() ? whenScriptEnds : script.poll();
                if (step == null) {
                    throw new IllegalStateException("script exhausted");
                }
                return step.get();
            }

            @Override
            public void addToolResults(List<ToolResult> results) {
                toolResultBatches.add(List.copyOf(results));
            }
        };
    }

    // ------------------------------------------------------------------ dựng vòng trả lời cho gọn

    public static ModelTurn text(String text) {
        return new ModelTurn(List.of(new Text(text)), StopReason.END_TURN, 10, 5);
    }

    public static ModelTurn refusal() {
        return new ModelTurn(List.of(), StopReason.REFUSAL, 10, 0);
    }

    public static ModelTurn tools(Block... blocks) {
        return new ModelTurn(List.of(blocks), StopReason.TOOL_USE, 10, 5);
    }

    public static ToolCall call(String id, String name, Map<String, Object> input) {
        return new ToolCall(id, name, input);
    }

    public static Text say(String text) {
        return new Text(text);
    }
}
