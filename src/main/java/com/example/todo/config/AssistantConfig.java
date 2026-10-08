package com.example.todo.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.example.todo.adapter.out.anthropic.AnthropicAssistantModelAdapter;
import com.example.todo.application.port.in.GetTodoUseCase;
import com.example.todo.application.port.in.ListTodosUseCase;
import com.example.todo.application.port.out.AssistantModelPort;
import com.example.todo.application.service.AssistantRateLimiter;
import com.example.todo.application.service.AssistantService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Nối trợ lý AI vào Spring. Cả lớp chỉ được nạp khi trợ lý được bật (xem ConditionalOnAssistantEnabled).
 */
@Configuration
@ConditionalOnAssistantEnabled
@EnableConfigurationProperties(AssistantProperties.class)
public class AssistantConfig {

    /**
     * Client Claude. API key lấy từ biến môi trường ANTHROPIC_API_KEY (qua Environment của Spring, nên test
     * có thể đặt giá trị giả), không bao giờ ghi ra log hay file cấu hình.
     */
    @Bean(destroyMethod = "close")
    public AnthropicClient anthropicClient(Environment environment, AssistantProperties properties) {
        return AnthropicOkHttpClient.builder()
                .fromEnv()
                .apiKey(environment.getRequiredProperty(ConditionalOnAssistantEnabled.API_KEY_VARIABLE))
                .timeout(properties.requestTimeout())
                .build();
    }

    @Bean
    public AssistantModelPort assistantModelPort(AnthropicClient client, AssistantProperties properties) {
        return new AnthropicAssistantModelAdapter(client, properties.model(), properties.maxTokens());
    }

    @Bean
    public AssistantService assistantService(ListTodosUseCase listTodos, GetTodoUseCase getTodo,
                                             AssistantModelPort model, Clock clock,
                                             AssistantProperties properties) {
        return new AssistantService(listTodos, getTodo, model,
                new AssistantRateLimiter(properties.rateLimitPerMinute(), clock));
    }

    /**
     * Luồng phát câu trả lời. Mỗi câu hỏi một virtual thread: phần lớn thời gian là chờ Claude trả lời,
     * virtual thread chờ không tốn luồng hệ điều hành. Số câu hỏi đồng thời đã bị giới hạn theo người dùng.
     */
    @Bean(destroyMethod = "close")
    public ExecutorService assistantExecutor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("assistant-", 0).factory());
    }
}
