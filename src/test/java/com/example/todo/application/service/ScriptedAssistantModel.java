package com.example.todo.application.service;

import com.example.todo.application.common.AssistantModelException;
import com.example.todo.application.port.in.ChatWithAssistantUseCase.ChatMessage;
import com.example.todo.application.port.out.AssistantModelPort;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Mô hình AI giả (fake) cho test: trả lần lượt các vòng đã soạn sẵn, phát văn bản từng mẩu như stream thật,
 * và ghi lại mọi thứ lõi gửi cho nó. Không gọi Claude thật.
 */
public class ScriptedAssistantModel implements AssistantModelPort {

    /** Một vòng trả lời: các mẩu văn bản phát lần lượt, rồi kết thúc vòng. failWhileStreaming: hỏng giữa chừng. */
    public record Step(List<String> chunks, ModelTurn turn, boolean failOnOpen, boolean failWhileStreaming) {
    }

    /** Một mẩu văn bản trong vòng trả lời (dùng khi dựng vòng bằng tools(...)). */
    public record Say(String text) {
    }

    private final Deque<Step> script = new ArrayDeque<>();
    private Step whenScriptEnds;

    public String systemPrompt;
    public List<ToolSpec> tools;
    public List<ChatMessage> history;
    public int calls;
    /** Mỗi phần tử là kết quả tool của một vòng (gửi trong một tin). */
    public final List<List<ToolResult>> toolResultBatches = new ArrayList<>();

    public ScriptedAssistantModel then(Step step) {
        script.add(step);
        return this;
    }

    /** Vòng tiếp theo hỏng ngay khi gửi request (ví dụ key sai, quá tải). */
    public ScriptedAssistantModel thenFail() {
        return then(new Step(List.of(), null, true, false));
    }

    /** Vòng tiếp theo phát vài mẩu văn bản rồi hỏng giữa chừng (ví dụ mất mạng). */
    public ScriptedAssistantModel thenFailWhileStreaming(String... chunks) {
        return then(new Step(List.of(chunks), null, false, true));
    }

    /** Hết kịch bản thì luôn trả vòng này (ví dụ mô hình gọi tool mãi không dừng). */
    public ScriptedAssistantModel forever(Step step) {
        whenScriptEnds = step;
        return this;
    }

    @Override
    public Session open(String systemPrompt, List<ToolSpec> tools, List<ChatMessage> history) {
        this.systemPrompt = systemPrompt;
        this.tools = tools;
        this.history = history;
        return new Session() {
            @Override
            public TurnStream next() {
                calls++;
                Step step = script.isEmpty() ? whenScriptEnds : script.poll();
                if (step == null) {
                    throw new IllegalStateException("script exhausted");
                }
                if (step.failOnOpen()) {
                    throw new AssistantModelException("simulated failure", null);
                }
                return listener -> {
                    for (String chunk : step.chunks()) {
                        if (!listener.onText(chunk)) {
                            return ModelTurn.cancelled();
                        }
                    }
                    if (step.failWhileStreaming()) {
                        throw new AssistantModelException("simulated stream failure", null);
                    }
                    return step.turn();
                };
            }

            @Override
            public void addToolResults(List<ToolResult> results) {
                toolResultBatches.add(List.copyOf(results));
            }
        };
    }

    // ------------------------------------------------------------------ dựng vòng trả lời cho gọn

    /** Vòng chỉ có văn bản, phát thành các mẩu đã cho rồi kết thúc. */
    public static Step text(String... chunks) {
        return new Step(List.of(chunks), new ModelTurn(List.of(), StopReason.END_TURN, 10, 5), false, false);
    }

    public static Step refusal() {
        return new Step(List.of(), new ModelTurn(List.of(), StopReason.REFUSAL, 10, 0), false, false);
    }

    /** Vòng gọi tool: các phần là Say (văn bản phát trước) hoặc ToolCall. */
    public static Step tools(Object... parts) {
        List<String> chunks = new ArrayList<>();
        List<ToolCall> calls = new ArrayList<>();
        for (Object part : parts) {
            if (part instanceof Say say) {
                chunks.add(say.text());
            } else {
                calls.add((ToolCall) part);
            }
        }
        return new Step(chunks, new ModelTurn(calls, StopReason.TOOL_USE, 10, 5), false, false);
    }

    public static ToolCall call(String id, String name, Map<String, Object> input) {
        return new ToolCall(id, name, input);
    }

    public static Say say(String text) {
        return new Say(text);
    }
}
