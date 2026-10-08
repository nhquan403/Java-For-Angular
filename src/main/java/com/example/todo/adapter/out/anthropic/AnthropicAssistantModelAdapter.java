package com.example.todo.adapter.out.anthropic;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
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
import java.util.List;
import java.util.Map;

/**
 * OUTBOUND ADAPTER: gọi Claude qua Claude Java SDK chính thức (com.anthropic:anthropic-java).
 *
 * Mỗi vòng là một request không stream: câu trả lời của mỗi vòng ngắn (effort LOW), và frontend nhận mỗi khối
 * văn bản thành một sự kiện delta, nên đơn giản hơn stream từng chữ mà hợp đồng với frontend vẫn như nhau.
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
        public ModelTurn next() {
            MessageCreateParams.Builder params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(maxTokens)
                    .system(systemPrompt)
                    .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                    .messages(messages);
            tools.forEach(params::addTool);

            Message response;
            try {
                response = client.messages().create(params.build());
            } catch (AnthropicServiceException e) {
                log.warn("Claude request failed: status={} type={}",
                        e.statusCode(), e.errorType().map(Object::toString).orElse("unknown"));
                throw new AssistantModelException("Claude request failed with status " + e.statusCode(), e);
            } catch (AnthropicException e) {
                log.warn("Claude request failed: {}", e.getClass().getSimpleName());
                throw new AssistantModelException("Claude request failed", e);
            }

            // Nguyên câu trả lời, kể cả khối thinking: vòng sau gửi lại đúng như vậy.
            messages.add(response.toParam());

            List<Block> blocks = new ArrayList<>();
            for (ContentBlock block : response.content()) {
                block.text().ifPresent(text -> blocks.add(new Text(text.text())));
                block.toolUse().ifPresent(use -> blocks.add(new ToolCall(use.id(), use.name(), toMap(use._input()))));
            }
            return new ModelTurn(blocks, stopReason(response),
                    response.usage().inputTokens(), response.usage().outputTokens());
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
