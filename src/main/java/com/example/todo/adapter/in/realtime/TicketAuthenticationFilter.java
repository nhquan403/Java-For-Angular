package com.example.todo.adapter.in.realtime;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Đăng nhập bằng ticket cho đúng hai đường: SSE và WebSocket. Đặt TRƯỚC bộ lọc đọc Bearer token (xem SecurityConfig).
 *
 * Ticket không hợp lệ thì không làm gì cả, request đi tiếp không có danh tính và bị chặn 401 như mọi
 * request thiếu token. Request đã có header Authorization thì bỏ qua ticket (client không phải trình duyệt).
 *
 * Cố ý KHÔNG đánh dấu @Component: nếu không Spring Boot tự đăng ký nó thêm một lần ở cấp servlet,
 * và ticket dùng một lần sẽ bị "ăn" ở lần chạy đầu. Nó được tạo bằng new trong SecurityConfig.
 */
public final class TicketAuthenticationFilter extends OncePerRequestFilter {

    private final RealtimeTicketStore tickets;

    public TicketAuthenticationFilter(RealtimeTicketStore tickets) {
        this.tickets = tickets;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        boolean realtimePath = path.equals(RealtimeEndpoints.STREAM) || path.equals(RealtimeEndpoints.WEBSOCKET);
        return !realtimePath
                || request.getParameter(RealtimeEndpoints.TICKET_PARAM) == null
                || request.getHeader(HttpHeaders.AUTHORIZATION) != null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        tickets.consume(request.getParameter(RealtimeEndpoints.TICKET_PARAM)).ifPresent(actor -> {
            // Cùng quy ước với JWT: name là id user, quyền ROLE_xxx (xem AuthenticatedActor).
            String role = actor.admin() ? "ROLE_ADMIN" : "ROLE_USER";
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    actor.userId().toString(), null, List.of(new SimpleGrantedAuthority(role))));
            SecurityContextHolder.setContext(context);
        });
        chain.doFilter(request, response);
    }
}
