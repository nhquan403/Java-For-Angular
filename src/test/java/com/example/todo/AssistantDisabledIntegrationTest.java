package com.example.todo;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trợ lý tắt (thiếu ANTHROPIC_API_KEY hoặc app.assistant.enabled=false): app vẫn chạy, endpoint trả 404 để
 * frontend hiện "trợ lý chưa được bật".
 */
class AssistantDisabledIntegrationTest {

    private static final Pattern ACCESS = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"");

    abstract static class DisabledAssistant {

        @Value("${local.server.port}")
        private int port;

        private final HttpClient http = HttpClient.newHttpClient();

        private HttpResponse<String> post(String path, String json, String bearer, String accept) throws Exception {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .header("Content-Type", "application/json")
                    .header("Accept", accept)
                    .POST(HttpRequest.BodyPublishers.ofString(json));
            if (bearer != null) {
                builder.header("Authorization", "Bearer " + bearer);
            }
            return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }

        @Test
        void chatEndpointIsNotRegistered() throws Exception {
            String credentials = "{\"email\":\"off-" + UUID.randomUUID().toString().substring(0, 8)
                    + "@example.com\",\"password\":\"password123\"}";
            assertThat(post("/api/auth/register", credentials, null, "application/json").statusCode()).isEqualTo(201);
            Matcher matcher = ACCESS.matcher(post("/api/auth/login", credentials, null, "application/json").body());
            assertThat(matcher.find()).isTrue();

            HttpResponse<String> response = post("/api/assistant/chat",
                    "{\"messages\":[{\"role\":\"user\",\"content\":\"Chào\"}]}", matcher.group(1), "text/event-stream");

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
