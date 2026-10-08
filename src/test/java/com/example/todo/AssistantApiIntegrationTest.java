package com.example.todo;

import com.example.todo.application.common.AssistantModelException;
import com.example.todo.application.port.out.AssistantModelPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test tích hợp hợp đồng SSE của POST /api/assistant/chat: khởi động cả ứng dụng (Spring Security, JWT, H2)
 * trên cổng ngẫu nhiên và gọi bằng HTTP thật. Mô hình AI được thay bằng bản giả, KHÔNG gọi Claude thật:
 * ANTHROPIC_API_KEY đặt giá trị giả chỉ để bật tính năng.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.security.bcrypt-strength=4",
                "ANTHROPIC_API_KEY=test-key-not-used",
                "app.assistant.rate-limit-per-minute=3"
        })
class AssistantApiIntegrationTest {

    /** Trả lời theo câu hỏi cuối, để mỗi test chọn được kịch bản mà không chia sẻ trạng thái. */
    @TestConfiguration
    static class FakeModelConfig {

        @Bean
        @Primary
        AssistantModelPort fakeAssistantModel() {
            return (systemPrompt, tools, history) -> {
                String question = history.getLast().content();
                List<AssistantModelPort.ModelTurn> turns = question.contains("soạn")
                        ? List.of(
                        new AssistantModelPort.ModelTurn(List.of(new AssistantModelPort.ToolCall("t1", "suggest_todo",
                                Map.of("title", "Chuẩn bị họp sprint", "description", "Gom việc"))),
                                AssistantModelPort.StopReason.TOOL_USE, 1, 1),
                        textTurn("Mình đã gợi ý."))
                        : List.of(textTurn("Xin chào \"bạn\"\nDòng 2"));
                return new AssistantModelPort.Session() {
                    private int index;

                    @Override
                    public AssistantModelPort.ModelTurn next() {
                        if (question.contains("hỏng")) {
                            throw new AssistantModelException("simulated", null);
                        }
                        return turns.get(Math.min(index++, turns.size() - 1));
                    }

                    @Override
                    public void addToolResults(List<AssistantModelPort.ToolResult> results) {
                    }
                };
            };
        }

        private static AssistantModelPort.ModelTurn textTurn(String text) {
            return new AssistantModelPort.ModelTurn(List.of(new AssistantModelPort.Text(text)),
                    AssistantModelPort.StopReason.END_TURN, 1, 1);
        }
    }

    @Value("${local.server.port}")
    private int port;

    private ApiTestClient api;
    private String token;

    @BeforeEach
    void login() throws Exception {
        api = new ApiTestClient(port);
        token = api.registerAndLogin(ApiTestClient.uniqueEmail("assistant")).access();
    }

    private HttpResponse<String> chat(String json) throws Exception {
        return api.send("POST", "/api/assistant/chat", json, token, "text/event-stream");
    }

    private static String ask(String question) {
        return "{\"messages\":[{\"role\":\"user\",\"content\":\"" + question + "\"}],"
                + "\"context\":{\"page\":\"todo-list\",\"path\":\"/todos\","
                + "\"query\":{\"completed\":false,\"page\":0,\"size\":10,\"sortBy\":\"createdAt\",\"direction\":\"desc\"},"
                + "\"todoId\":null,\"createDraft\":null}}";
    }

    private static void assertProblem(HttpResponse<String> response, int status) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
        assertThat(response.body()).contains("\"status\":" + status);
    }

    // ------------------------------------------------------------------ hợp đồng SSE

    @Test
    void streamsServerSentEventsInTheAgreedFormat() throws Exception {
        HttpResponse<String> response = chat(ask("Chào"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("text/event-stream");
        assertThat(response.headers().firstValue("X-Request-Id")).isPresent();
        assertThat(response.body()).isEqualTo(
                "event: delta\ndata: {\"text\":\"Xin chào \\\"bạn\\\"\\nDòng 2\"}\n\n"
                        + "event: done\ndata: {}\n\n");
    }

    @Test
    void streamsToolAndSuggestionEvents() throws Exception {
        HttpResponse<String> response = chat(ask("soạn giúp tôi todo"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(
                "event: tool\ndata: {\"name\":\"suggest_todo\"}\n\n"
                        + "event: suggestion\ndata: {\"title\":\"Chuẩn bị họp sprint\",\"description\":\"Gom việc\"}\n\n"
                        + "event: delta\ndata: {\"text\":\"Mình đã gợi ý.\"}\n\n"
                        + "event: done\ndata: {}\n\n");
    }

    @Test
    void acceptsAConversationWithContextOmitted() throws Exception {
        HttpResponse<String> response = chat("{\"messages\":[{\"role\":\"user\",\"content\":\"Chào\"},"
                + "{\"role\":\"assistant\",\"content\":\"Chào bạn\"},{\"role\":\"user\",\"content\":\"Chào lại\"}]}");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).endsWith("event: done\ndata: {}\n\n");
    }

    // ------------------------------------------------------------------ lỗi trước khi stream

    @Test
    void requiresLogin() throws Exception {
        HttpResponse<String> response = api.send("POST", "/api/assistant/chat", ask("Chào"), null, "text/event-stream");

        assertProblem(response, 401);
    }

    @Test
    void rejectsInvalidRequestsWithProblemDetail() throws Exception {
        String tooMany = "{\"messages\":[" + String.join(",",
                Collections.nCopies(21, "{\"role\":\"user\",\"content\":\"a\"}")) + "]}";

        assertProblem(chat("{\"messages\":[{\"role\":\"system\",\"content\":\"a\"}]}"), 400);
        assertProblem(chat("{\"messages\":[{\"role\":\"assistant\",\"content\":\"a\"},"
                + "{\"role\":\"user\",\"content\":\"b\"}]}"), 400);
        assertProblem(chat("{\"messages\":[{\"role\":\"user\",\"content\":\"" + "x".repeat(4001) + "\"}]}"), 400);
        assertProblem(chat(tooMany), 400);
        assertProblem(chat("{\"messages\":[]}"), 400);
        assertProblem(chat("{}"), 400);
        assertProblem(chat("{\"messages\":[{\"role\":\"user\",\"content\":\"a\"}],\"context\":{\"page\":\"x\"}}"), 400);
        assertProblem(chat("not json"), 400);
    }

    @Test
    void returns429WhenTheRateLimitIsExceeded() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(chat(ask("Chào")).statusCode()).isEqualTo(200);
        }

        HttpResponse<String> limited = chat(ask("Chào"));

        assertProblem(limited, 429);
        assertThat(limited.headers().firstValue("Retry-After")).isPresent();
    }

    @Test
    void returns503WhenTheModelFailsBeforeStreaming() throws Exception {
        assertProblem(chat(ask("hỏng")), 503);
    }

    @Test
    void isDocumentedInOpenApiAsAnEventStream() throws Exception {
        HttpResponse<String> docs = api.send("GET", "/v3/api-docs", null, null);

        assertThat(docs.statusCode()).isEqualTo(200);
        assertThat(docs.body()).contains("/api/assistant/chat").contains("text/event-stream");
    }
}
