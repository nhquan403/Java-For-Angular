package com.example.todo.adapter.in.web;

import com.example.todo.application.common.Actor;
import org.springframework.security.core.Authentication;

/**
 * Đổi thông tin đăng nhập của Spring Security thành Actor của lõi ứng dụng.
 * Đây là nơi duy nhất controller "dịch" từ thế giới Spring sang thế giới của lõi.
 *
 * Với token JWT: name = claim "sub" (id user), authorities lấy từ claim "roles" (xem SecurityConfig).
 * Lưu ý role đọc từ token nên đổi role có hiệu lực khi access token hết hạn (tối đa vài phút).
 */
public final class AuthenticatedActor {

    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    private AuthenticatedActor() {
    }

    public static Actor from(Authentication authentication) {
        Long userId = Long.valueOf(authentication.getName());
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> ADMIN_AUTHORITY.equals(authority.getAuthority()));
        return new Actor(userId, admin);
    }
}
