package com.example.todo.application.service;

import com.example.todo.application.common.TooManyAssistantRequestsException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Giới hạn số câu hỏi trợ lý của mỗi người trong một phút (cửa sổ trượt), giữ trong bộ nhớ.
 * Chạy nhiều instance thì mỗi instance đếm riêng, giới hạn thực tế nhân lên theo số instance.
 *
 * Thread-safe: compute khóa theo từng userId nên hai request đồng thời không cùng lọt qua giới hạn.
 */
public class AssistantRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final int limitPerMinute;
    private final Clock clock;
    private final ConcurrentHashMap<Long, Deque<Instant>> byUser = new ConcurrentHashMap<>();

    public AssistantRateLimiter(int limitPerMinute, Clock clock) {
        if (limitPerMinute < 1) {
            throw new IllegalArgumentException("limitPerMinute must be >= 1");
        }
        this.limitPerMinute = limitPerMinute;
        this.clock = clock;
    }

    /** @throws TooManyAssistantRequestsException đã hỏi đủ số lần trong phút vừa qua */
    public void acquire(Long userId) {
        Instant now = clock.instant();
        Instant windowStart = now.minus(WINDOW);
        byUser.compute(userId, (id, current) -> {
            Deque<Instant> calls = current != null ? current : new ArrayDeque<>();
            while (!calls.isEmpty() && !calls.peekFirst().isAfter(windowStart)) {
                calls.pollFirst();
            }
            if (calls.size() >= limitPerMinute) {
                long retryAfter = Math.max(1, Duration.between(windowStart, calls.peekFirst()).toSeconds() + 1);
                throw new TooManyAssistantRequestsException(limitPerMinute, retryAfter);
            }
            calls.addLast(now);
            return calls;
        });
    }
}
