package com.example.todo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Cấu hình trợ lý AI, đọc từ app.assistant.* (đều có mặc định). API key KHÔNG nằm ở đây:
 * chỉ đọc từ biến môi trường ANTHROPIC_API_KEY.
 *
 * @param enabled        false thì tắt hẳn endpoint /api/assistant/** (404), kể cả khi có API key
 * @param model          model Claude
 * @param maxTokens      số token tối đa của một vòng trả lời (gồm cả phần suy nghĩ)
 * @param rateLimitPerMinute số câu hỏi tối đa của một người trong một phút
 * @param requestTimeout thời gian chờ tối đa một lần gọi Claude
 * @param streamTimeout  thời gian tối đa của cả một câu trả lời (luồng SSE)
 */
@ConfigurationProperties(prefix = "app.assistant")
public record AssistantProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("claude-opus-5-5") String model,
        @DefaultValue("16000") long maxTokens,
        @DefaultValue("20") int rateLimitPerMinute,
        @DefaultValue("60s") Duration requestTimeout,
        @DefaultValue("5m") Duration streamTimeout) {
}
