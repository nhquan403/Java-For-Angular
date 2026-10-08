package com.example.todo.adapter.out.anthropic;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.helpers.MessageAccumulator;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.example.todo.application.common.AssistantModelException;
import com.example.todo.application.port.in.ChatWithAssistantUseCase.ChatMessage;
import com.example.todo.application.port.in.ChatWithAssistantUseCase.Role;
import com.example.todo.application.port.out.AssistantModelPort;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * OUTBOUND ADAPTER: gọi Claude qua Claude Java SDK chính thức (com.anthropic:anthropic-java).
 *
 * Mỗi vòng là một request stream (createStreaming): chữ Claude sinh ra tới đâu được chuyển cho lõi tới đó,
 * MessageAccumulator ghép lại câu trả lời đầy đủ để gửi lại ở vòng sau.
 *
 * Cấu hình gửi đi: thinking để mặc định (model luôn bật, gửi "disabled" sẽ bị 400), effort LOW,
 * tool_choice để mặc định auto (ép "any"/"tool" sẽ bị 400), tool strict + additionalProperties=false.
 * Mỗi vòng thêm NGUYÊN câu trả lời (kể cả khối thinking) vào lịch sử trước khi gửi kết quả tool.
 *
 * Không ghi nội dung hội thoại ra log.
 */
public class AnthropicAssistantModelAdapter implements AssistantModelPort {

    private static final Logger log = LoggerFactory.getLogger(AnthropicAssistantModelAdapter.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final AnthropicClient client;
    private final String model;
    private final long maxTokens;

    public AnthropicAssistantModelAdapter(AnthropicClient client, String model, long maxTokens) {
        this.client = client;
        this.model = model;
        this.maxTokens = maxTokens;
    }

    @Override
    public Session open(String systemPrompt, List<ToolSpec> tools, List<ChatMessage> history) {
        List<Tool> sdkTools = tools.stream().map(AnthropicAssistantModelAdapter::toSdkTool).toList();
        List<MessageParam> messages = new ArrayList<>(history.size() + 8);
        for (ChatMessage message : history) {
            messages.add(MessageParam.builder()
                    .role(message.role() == Role.USER ? MessageParam.Role.USER : MessageParam.Role.ASSISTANT)
                    .content(message.content())
                    .build());
        }
        return new ClaudeSession(systemPrompt, sdkTools, messages);
    }

    private final class ClaudeSession implements Session {

        private final String systemPrompt;
        private final List<Tool> tools;
        private final List<MessageParam> messages;

        private ClaudeSession(String systemPrompt, List<Tool> tools, List<MessageParam> messages) {
            this.systemPrompt = systemPrompt;
            this.tools = tools;
            this.messages = messages;
        }

        @Override
        public TurnStream next() {
            MessageCreateParams.Builder params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(maxTokens)
                    .system(systemPrompt)
                    .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                    .messages(messages);
            tools.forEach(params::addTool);

            // Lỗi HTTP (key sai, hết credit, quá tải...) ném ngay ở đây, trước khi có chữ nào.
            StreamResponse<RawMessageStreamEvent> response;
            try {
                response = client.messages().createStreaming(params.build());
            } catch (AnthropicException e) {
                throw failure(e);
            }
            return listener -> read(response, listener);
        }

        private ModelTurn read(StreamResponse<RawMessageStreamEvent> response, TextListener listener) {
            MessageAccumulator accumulator = MessageAccumulator.create();
            // try-with-resources: client ngắt giữa chừng thì đóng luồng, Claude ngừng sinh và ngừng tính token.
            try (response) {
                Iterator<RawMessageStreamEvent> events = response.stream().iterator();
                while (events.hasNext()) {
                    RawMessageStreamEvent event = accumulator.accumulate(events.next());
                    String text = event.contentBlockDelta()
                            .flatMap(delta -> delta.delta().text())
                            .map(textDelta -> textDelta.text())
                            .orElse("");
                    if (!text.isEmpty() && !listener.onText(text)) {
                        return ModelTurn.cancelled();
                    }
                }
            } catch (AnthropicException e) {
                throw failure(e);
            }

            Message message = accumulator.message();
            // Nguyên câu trả lời, kể cả khối thinking: vòng sau gửi lại đúng như vậy.
            messages.add(message.toParam());

            List<ToolCall> toolCalls = new ArrayList<>();
            for (ContentBlock block : message.content()) {
                block.toolUse().ifPresent(use -> toolCalls.add(new ToolCall(use.id(), use.name(), toMap(use._input()))));
            }
            return new ModelTurn(toolCalls, stopReason(message),
                    message.usage().inputTokens(), message.usage().outputTokens());
        }

        @Override
        public void addToolResults(List<ToolResult> results) {
            List<ContentBlockParam> blocks = results.stream()
                    .map(result -> ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                            .toolUseId(result.toolCallId())
                            .content(result.content())
                            .isError(result.error())
                            .build()))
                    .toList();
            messages.add(MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .contentOfBlockParams(blocks)
                    .build());
        }
    }

    /** Ghi log lý do (không có nội dung hội thoại) rồi đổi thành lỗi của lõi. */
    private static AssistantModelException failure(AnthropicException e) {
        if (e instanceof AnthropicServiceException service) {
            // Thông báo lỗi của API (key sai, hết credit, model không có quyền...) để biết vì sao 503.
            log.warn("Claude request failed: status={} type={} message={}", service.statusCode(),
                    service.errorType().map(Object::toString).orElse("unknown"), service.getMessage());
            return new AssistantModelException("Claude request failed with status " + service.statusCode(), e);
        }
        // Thường là lỗi mạng: không phân giải được tên miền, proxy, chứng chỉ TLS, hết thời gian chờ.
        log.warn("Claude request failed: {} cause={}", e.getClass().getSimpleName(), rootCause(e));
        return new AssistantModelException("Claude request failed", e);
    }

    /**
     * Đổi stop_reason của SDK sang StopReason của port. Kiểu của SDK phải viết tên đầy đủ vì
     * AssistantModelPort cũng có StopReason (lớp lồng được kế thừa che mất import).
     */
    private static StopReason stopReason(Message response) {
        var value = response.stopReason()
                .map(com.anthropic.models.messages.StopReason::value)
                .orElse(com.anthropic.models.messages.StopReason.Value._UNKNOWN);
        return switch (value) {
            case END_TURN -> StopReason.END_TURN;
            case TOOL_USE -> StopReason.TOOL_USE;
            case MAX_TOKENS -> StopReason.MAX_TOKENS;
            case REFUSAL -> StopReason.REFUSAL;
            default -> StopReason.OTHER;
        };
    }

    private static String rootCause(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }

    private static Map<String, Object> toMap(JsonValue input) {
        Map<String, Object> map = input.convert(MAP_TYPE);
        return map != null ? map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static Tool toSdkTool(ToolSpec spec) {
        Map<String, Object> schema = spec.inputSchema();
        Tool.InputSchema.Properties.Builder properties = Tool.InputSchema.Properties.builder();
        ((Map<String, Object>) schema.get("properties"))
                .forEach((name, property) -> properties.putAdditionalProperty(name, JsonValue.from(property)));
        return Tool.builder()
                .name(spec.name())
                .description(spec.description())
                .strict(true)
                .inputSchema(Tool.InputSchema.builder()
                        .properties(properties.build())
                        .required((List<String>) schema.get("required"))
                        // Từ khóa JSON Schema "additionalProperties": false. Không nhầm với
                        // InputSchema.Builder.additionalProperties(Map), hàm đó dùng để thêm trường tùy ý.
                        .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                        .build())
                .build();
    }
}
