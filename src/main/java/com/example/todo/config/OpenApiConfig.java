package com.example.todo.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tài liệu API tự sinh (springdoc). Khi chạy profile dev:
 *   Swagger UI : http://localhost:8080/swagger-ui.html
 *   JSON       : http://localhost:8080/v3/api-docs
 * Profile prod tắt các endpoint này.
 *
 * Trên Swagger UI bấm nút "Authorize", dán access token (không cần chữ "Bearer") để gọi API có khóa.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI todoOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Todo API")
                        .version("v1")
                        .description("Todo API xây dựng theo kiến trúc Hexagonal, có JWT và phân quyền"))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
