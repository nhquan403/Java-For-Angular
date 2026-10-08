package com.example.todo.adapter.in.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Gắn mã theo dõi (request id) cho mỗi request:
 * - lấy từ header X-Request-Id nếu client gửi (và hợp lệ), không thì tự sinh;
 * - đưa vào MDC để mọi dòng log của request này đều có mã đó (xem logging.pattern.level);
 * - trả lại trong header response để client báo lỗi kèm mã, dễ tra log;
 * - ghi một dòng log tóm tắt: method, đường dẫn, status, thời gian.
 */
@Component
// Chạy TRƯỚC Spring Security (filter của nó có thứ tự -100) để cả response 401/403
// cũng có request id và dòng log tóm tắt.
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader(HEADER));
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long millis = (System.nanoTime() - start) / 1_000_000;
            log.info("{} {} -> {} ({} ms)",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), millis);
            MDC.remove(MDC_KEY);
        }
    }

    /** Bỏ qua health check để log không bị ngập. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    /** Chỉ nhận id an toàn từ client, tránh nhét ký tự lạ vào log (log injection). */
    private static String resolveRequestId(String fromClient) {
        if (fromClient != null && SAFE_ID.matcher(fromClient).matches()) {
            return fromClient;
        }
        return UUID.randomUUID().toString();
    }
}
