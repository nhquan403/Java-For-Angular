package com.example.todo.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Trả lỗi 401 và 403 cùng định dạng ProblemDetail (RFC 9457) như phần còn lại của API.
 *
 * Lỗi bảo mật xảy ra trong filter, TRƯỚC khi tới controller, nên @RestControllerAdvice
 * (GlobalExceptionHandler) không bắt được. Vì vậy cần hai handler riêng này.
 *
 * JSON viết tay vì chỉ có chuỗi cố định, không có dữ liệu người dùng nên không cần thư viện JSON.
 */
@Component
public class ProblemJsonSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    /** 401: chưa đăng nhập, token sai, bị sửa hoặc hết hạn. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        write(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized",
                "Authentication is required, or the access token is invalid or expired");
    }

    /** 403: đã đăng nhập nhưng không đủ quyền (ví dụ USER gọi API của ADMIN). */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden",
                "You do not have permission to access this resource");
    }

    private static void write(HttpServletResponse response, int status, String title, String detail)
            throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/problem+json");
        response.getWriter().write(
                "{\"type\":\"about:blank\",\"title\":\"" + title + "\",\"status\":" + status
                        + ",\"detail\":\"" + detail + "\"}");
    }
}
