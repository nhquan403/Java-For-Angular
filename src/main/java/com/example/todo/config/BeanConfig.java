package com.example.todo.config;

import com.example.todo.application.port.out.PasswordHasherPort;
import com.example.todo.application.port.out.RefreshTokenRepositoryPort;
import com.example.todo.application.port.out.TodoEventPublisherPort;
import com.example.todo.application.port.out.TodoRepositoryPort;
import com.example.todo.application.port.out.TokenPort;
import com.example.todo.application.port.out.UserRepositoryPort;
import com.example.todo.application.service.AuthService;
import com.example.todo.application.service.TodoService;
import com.example.todo.application.service.UserService;
import com.example.todo.application.port.in.EnsureAdminUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/**
 * Nơi duy nhất "nối dây" lõi ứng dụng với Spring.
 * Các service không có @Service nên lõi không phải import annotation đăng ký bean của Spring.
 */
@Configuration
public class BeanConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public TodoService todoService(TodoRepositoryPort repository, TodoEventPublisherPort events, Clock clock) {
        return new TodoService(repository, events, clock);
    }

    @Bean
    public AuthService authService(UserRepositoryPort users,
                                   RefreshTokenRepositoryPort refreshTokens,
                                   PasswordHasherPort hasher,
                                   TokenPort tokens,
                                   Clock clock,
                                   @Value("${app.security.jwt.refresh-token-ttl}") Duration refreshTokenTtl) {
        return new AuthService(users, refreshTokens, hasher, tokens, clock, refreshTokenTtl);
    }

    @Bean
    public UserService userService(UserRepositoryPort users,
                                   RefreshTokenRepositoryPort refreshTokens,
                                   Clock clock) {
        return new UserService(users, refreshTokens, clock);
    }

    /**
     * Tạo tài khoản ADMIN đầu tiên lúc khởi động nếu cấu hình có email và mật khẩu.
     * Nếu email đã tồn tại thì bỏ qua. Production nên bỏ hai biến này sau lần chạy đầu.
     */
    @Bean
    public ApplicationRunner bootstrapAdmin(EnsureAdminUseCase ensureAdmin,
                                            @Value("${app.security.bootstrap-admin.email:}") String email,
                                            @Value("${app.security.bootstrap-admin.password:}") String password) {
        return args -> {
            if (!email.isBlank() && !password.isBlank()) {
                ensureAdmin.ensureAdmin(email, password);
            }
        };
    }
}
