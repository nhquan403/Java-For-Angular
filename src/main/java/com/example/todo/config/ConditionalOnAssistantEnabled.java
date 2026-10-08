package com.example.todo.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Chỉ đăng ký bean (cấu hình trợ lý, controller /api/assistant/**) khi trợ lý AI được bật:
 * app.assistant.enabled khác false VÀ có biến môi trường ANTHROPIC_API_KEY.
 * Thiếu một trong hai thì endpoint không tồn tại (404), phần còn lại của app chạy bình thường.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(ConditionalOnAssistantEnabled.AssistantEnabledCondition.class)
public @interface ConditionalOnAssistantEnabled {

    String API_KEY_VARIABLE = "ANTHROPIC_API_KEY";

    class AssistantEnabledCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            var environment = context.getEnvironment();
            return environment.getProperty("app.assistant.enabled", Boolean.class, true)
                    && StringUtils.hasText(environment.getProperty(API_KEY_VARIABLE));
        }
    }
}
