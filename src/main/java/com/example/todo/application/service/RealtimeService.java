package com.example.todo.application.service;

import com.example.todo.application.common.Actor;
import com.example.todo.application.common.TooManySubscriptionsException;
import com.example.todo.application.port.in.NotifyTodoEventUseCase;
import com.example.todo.application.port.in.SubscribeToTodoEventsUseCase;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Sổ đăng ký các client đang kết nối và quy tắc "ai được nhận sự kiện nào". Thuần Java, giữ trong bộ nhớ.
 *
 * Mỗi instance của ứng dụng chỉ biết client nối vào CHÍNH NÓ. Khi chạy nhiều instance, mỗi instance tự nhận
 * sự kiện từ Kafka (mỗi instance một consumer group riêng, xem TodoEventKafkaListener) rồi giao cho client của mình.
 *
 * Thread-safe: subscribe, close và notify được gọi đồng thời từ nhiều luồng.
 */
public class RealtimeService implements SubscribeToTodoEventsUseCase, NotifyTodoEventUseCase {

    private final int maxPerUser;

    /** userId -> các đăng ký của user đó. Dùng để giới hạn số kết nối và tìm người nhận. */
    private final ConcurrentHashMap<Long, Set<Subscriber>> byUser = new ConcurrentHashMap<>();

    /** Các đăng ký của ADMIN, nhận mọi sự kiện. Là tập con của byUser, giữ riêng để không phải quét hết. */
    private final Set<Subscriber> admins = ConcurrentHashMap.newKeySet();

    public RealtimeService(int maxPerUser) {
        if (maxPerUser < 1) {
            throw new IllegalArgumentException("maxPerUser must be >= 1");
        }
        this.maxPerUser = maxPerUser;
    }

    @Override
    public Subscription subscribe(Actor actor, Consumer<String> sink) {
        Subscriber subscriber = new Subscriber(actor, sink);
        // compute chạy nguyên tử theo từng khóa: kiểm tra giới hạn và thêm là một bước, hai request
        // đồng thời của cùng một user không thể cùng lọt qua giới hạn.
        byUser.compute(actor.userId(), (id, current) -> {
            Set<Subscriber> set = current != null ? current : ConcurrentHashMap.newKeySet();
            if (set.size() >= maxPerUser) {
                throw new TooManySubscriptionsException(maxPerUser);
            }
            set.add(subscriber);
            return set;
        });
        if (actor.admin()) {
            admins.add(subscriber);
        }
        return subscriber;
    }

    @Override
    public int notifyEvent(Long ownerId, String payload) {
        int delivered = 0;
        // Không đặt cùng một người vào hai danh sách: ADMIN là chủ todo thì có mặt ở cả byUser lẫn admins.
        if (ownerId != null) {
            Set<Subscriber> owners = byUser.get(ownerId);
            if (owners != null) {
                for (Subscriber s : owners) {
                    if (!s.actor.admin() && s.deliver(payload)) {
                        delivered++;
                    }
                }
            }
        }
        for (Subscriber s : admins) {
            if (s.deliver(payload)) {
                delivered++;
            }
        }
        return delivered;
    }

    /** Số kết nối đang mở, để theo dõi và test. */
    public int connectionCount() {
        return byUser.values().stream().mapToInt(Set::size).sum();
    }

    private void remove(Subscriber subscriber) {
        byUser.computeIfPresent(subscriber.actor.userId(), (id, set) -> {
            set.remove(subscriber);
            return set.isEmpty() ? null : set; // trả null để xóa hẳn khóa, tránh rò rỉ bộ nhớ
        });
        admins.remove(subscriber);
    }

    private final class Subscriber implements Subscription {

        private final Actor actor;
        private final Consumer<String> sink;

        private Subscriber(Actor actor, Consumer<String> sink) {
            this.actor = actor;
            this.sink = sink;
        }

        /** @return true nếu giao thành công. Sink lỗi thì hủy đăng ký, các client khác không bị ảnh hưởng. */
        private boolean deliver(String payload) {
            try {
                sink.accept(payload);
                return true;
            } catch (RuntimeException e) {
                remove(this);
                return false;
            }
        }

        @Override
        public void close() {
            remove(this);
        }
    }
}
