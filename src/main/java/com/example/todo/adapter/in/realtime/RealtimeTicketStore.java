package com.example.todo.adapter.in.realtime;

import com.example.todo.application.common.Actor;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kho "vé" dùng một lần để mở SSE hoặc WebSocket.
 *
 * Vì sao cần? Trình duyệt không cho gắn header Authorization vào EventSource hay WebSocket, mà đưa access token
 * lên URL thì lộ trong log và lịch sử. Cách làm: client đã đăng nhập gọi POST /api/realtime/ticket (có header
 * bình thường) để lấy một vé ngẫu nhiên sống vài chục giây, rồi mở kết nối với ?ticket=... Vé dùng đúng một lần,
 * nên dù nằm trong URL và bị lộ thì cũng vô dụng ngay sau đó.
 *
 * Giữ trong bộ nhớ nên chỉ đúng khi vé được phát và dùng ở cùng một instance. Chạy nhiều instance thì cần
 * sticky session hoặc chuyển kho này sang Redis.
 */
public final class RealtimeTicketStore {

    private static final int TICKET_BYTES = 32;

    private record Entry(Actor actor, Instant expiresAt) {
    }

    private final ConcurrentHashMap<String, Entry> tickets = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;
    private final Duration ttl;
    private final int maxPending;

    public RealtimeTicketStore(Clock clock, Duration ttl, int maxPending) {
        this.clock = clock;
        this.ttl = ttl;
        this.maxPending = maxPending;
    }

    /** @return vé mới, hoặc rỗng nếu đang có quá nhiều vé chưa dùng (chống làm đầy bộ nhớ). */
    public Optional<String> issue(Actor actor) {
        if (tickets.size() >= maxPending) {
            purgeExpired();
            if (tickets.size() >= maxPending) {
                return Optional.empty();
            }
        }
        byte[] bytes = new byte[TICKET_BYTES];
        random.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tickets.put(ticket, new Entry(actor, clock.instant().plus(ttl)));
        return Optional.of(ticket);
    }

    /** Dùng vé: lấy ra và xóa luôn trong một bước nguyên tử, nên hai lần dùng đồng thời chỉ một lần thành công. */
    public Optional<Actor> consume(String ticket) {
        if (ticket == null) {
            return Optional.empty();
        }
        Entry entry = tickets.remove(ticket);
        if (entry == null || !clock.instant().isBefore(entry.expiresAt())) {
            return Optional.empty();
        }
        return Optional.of(entry.actor());
    }

    public void purgeExpired() {
        Instant now = clock.instant();
        tickets.values().removeIf(e -> !now.isBefore(e.expiresAt()));
    }

    public int pendingCount() {
        return tickets.size();
    }
}
