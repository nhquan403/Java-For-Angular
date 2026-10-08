package com.example.todo.application.service;

import com.example.todo.application.common.Actor;
import com.example.todo.application.common.AssistantEvent;
import com.example.todo.application.common.AssistantModelException;
import com.example.todo.application.port.in.ChatWithAssistantUseCase;
import com.example.todo.application.port.in.GetTodoUseCase;
import com.example.todo.application.port.in.ListTodosUseCase;
import com.example.todo.application.port.out.AssistantModelPort;
import com.example.todo.application.port.out.AssistantModelPort.ModelTurn;
import com.example.todo.application.port.out.AssistantModelPort.StopReason;
import com.example.todo.application.port.out.AssistantModelPort.ToolCall;
import com.example.todo.application.port.out.AssistantModelPort.ToolResult;
import com.example.todo.application.port.out.AssistantModelPort.TurnStream;

import java.util.ArrayList;
import java.util.List;

/**
 * Trợ lý AI: vòng lặp "mô hình trả lời -> chạy tool -> gửi kết quả -> mô hình trả lời tiếp".
 * Văn bản của mô hình được phát cho client ngay khi sinh ra (từng mẩu một), như các ứng dụng chat AI.
 * Tool nằm ở AssistantTools, system prompt ở AssistantPrompt.
 */
public class AssistantService implements ChatWithAssistantUseCase {

    /** Số vòng chạy tool tối đa cho một câu hỏi. Mô hình xin thêm vòng nữa thì dừng và báo lỗi. */
    public static final int MAX_TOOL_ROUNDS = 6;

    static final String REFUSAL_MESSAGE = "Trợ lý không thể hỗ trợ yêu cầu này. Bạn thử diễn đạt theo cách khác nhé.";
    static final String TOO_MANY_ROUNDS_MESSAGE = "Câu hỏi cần tra cứu quá nhiều bước. Bạn thử hỏi cụ thể hơn nhé.";
    static final String MODEL_ERROR_MESSAGE = "Trợ lý đang gặp sự cố nên chưa trả lời xong. Bạn thử lại sau nhé.";

    private final AssistantTools tools;
    private final AssistantModelPort model;
    private final AssistantRateLimiter rateLimiter;

    public AssistantService(ListTodosUseCase listTodos, GetTodoUseCase getTodo,
                            AssistantModelPort model, AssistantRateLimiter rateLimiter) {
        this.tools = new AssistantTools(listTodos, getTodo);
        this.model = model;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public Reply start(Command command) {
        rateLimiter.acquire(command.actor().userId());
        AssistantModelPort.Session session = model.open(
                AssistantPrompt.build(command.actor(), command.context()),
                AssistantTools.DEFINITIONS,
                command.messages());
        // Mở luồng vòng đầu ngay trên luồng request: lỗi (key sai, quá tải...) còn kịp thành HTTP 503.
        TurnStream firstTurn = session.next();
        return sink -> new Conversation(command.actor(), session, sink).run(firstTurn);
    }

    /** Một lần trả lời. Chỉ chạy trên một luồng. */
    private final class Conversation {

        private final Actor actor;
        private final AssistantModelPort.Session session;
        private final AssistantEventSink sink;
        private int toolRounds;
        private long inputTokens;
        private long outputTokens;

        private Conversation(Actor actor, AssistantModelPort.Session session, AssistantEventSink sink) {
            this.actor = actor;
            this.session = session;
            this.sink = sink;
        }

        private Outcome run(TurnStream firstTurn) {
            TurnStream stream = firstTurn;
            while (true) {
                // 1. Nhận câu trả lời, phát từng mẩu văn bản cho client ngay khi có.
                ModelTurn turn;
                try {
                    turn = stream.read(text -> sink.emit(new AssistantEvent.Delta(text)));
                } catch (AssistantModelException e) {
                    return fail(Outcome.Status.MODEL_ERROR, MODEL_ERROR_MESSAGE);
                }
                if (turn.stopReason() == StopReason.CANCELLED) {
                    return finish(Outcome.Status.CANCELLED);
                }
                inputTokens += turn.inputTokens();
                outputTokens += turn.outputTokens();

                // 2. Mô hình dừng thì kết thúc.
                if (turn.stopReason() == StopReason.REFUSAL) {
                    return fail(Outcome.Status.REFUSED, REFUSAL_MESSAGE);
                }
                if (turn.stopReason() != StopReason.TOOL_USE || turn.toolCalls().isEmpty()) {
                    return sink.emit(new AssistantEvent.Done())
                            ? finish(Outcome.Status.COMPLETED)
                            : finish(Outcome.Status.CANCELLED);
                }
                if (toolRounds == MAX_TOOL_ROUNDS) {
                    return fail(Outcome.Status.TOO_MANY_TOOL_ROUNDS, TOO_MANY_ROUNDS_MESSAGE);
                }

                // 3. Chạy mọi tool của vòng này, gửi tất cả kết quả trong một lần.
                toolRounds++;
                List<ToolResult> results = new ArrayList<>(turn.toolCalls().size());
                for (ToolCall call : turn.toolCalls()) {
                    if (!sink.emit(new AssistantEvent.ToolUse(call.name()))) {
                        return finish(Outcome.Status.CANCELLED);
                    }
                    AssistantTools.Execution execution = tools.execute(actor, call);
                    if (execution.suggestion() != null && !sink.emit(execution.suggestion())) {
                        return finish(Outcome.Status.CANCELLED);
                    }
                    results.add(execution.result());
                }
                session.addToolResults(results);

                // 4. Hỏi mô hình vòng tiếp theo.
                try {
                    stream = session.next();
                } catch (AssistantModelException e) {
                    return fail(Outcome.Status.MODEL_ERROR, MODEL_ERROR_MESSAGE);
                }
            }
        }

        private Outcome fail(Outcome.Status status, String message) {
            sink.emit(new AssistantEvent.Error(message));
            return finish(status);
        }

        private Outcome finish(Outcome.Status status) {
            return new Outcome(status, toolRounds, inputTokens, outputTokens);
        }
    }
}
