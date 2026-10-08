package com.example.todo;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trợ lý tắt (thiếu ANTHROPIC_API_KEY hoặc app.assistant.enabled=false): app vẫn chạy, endpoint trả 404 để
 * frontend hiện "trợ lý chưa được bật".
 */
class AssistantDisabledIntegrationTest {

    abstract static class DisabledAssistant {

        @Value("${local.server.port}")
        private int port;

        @Test
        void chatEndpointIsNotRegistered() throws Exception {
            ApiTestClient api = new ApiTestClient(port);
            String token = api.registerAndLogin(ApiTestClient.uniqueEmail("off")).access();

            HttpResponse<String> response = api.send("POST", "/api/assistant/chat",
                    "{\"messages\":[{\"role\":\"user\",\"content\":\"Chào\"}]}", token, "text/event-stream");

            assertThat(response.statusCode()).isEqualTo(404);
            assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {"app.security.bcrypt-strength=4", "ANTHROPIC_API_KEY="})
    class WithoutApiKey extends DisabledAssistant {
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {"app.security.bcrypt-strength=4", "ANTHROPIC_API_KEY=test-key-not-used",
                    "app.assistant.enabled=false"})
    class TurnedOff extends DisabledAssistant {
    }
}
