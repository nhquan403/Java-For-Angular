package com.example.todo.application.service;

import com.example.todo.application.common.Actor;
import com.example.todo.application.common.TooManySubscriptionsException;
import com.example.todo.application.port.in.SubscribeToTodoEventsUseCase.Subscription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RealtimeServiceTest {

    private static final Actor ALICE = Actor.user(1L);
    private static final Actor BOB = Actor.user(2L);
    private static final Actor ADMIN = Actor.admin(99L);

    private RealtimeService service;

    @BeforeEach
    void setUp() {
        service = new RealtimeService(2);
    }

    @Test
    void userOnlyReceivesEventsOfTheirOwnTodos() {
        List<String> alice = new ArrayList<>();
        List<String> bob = new ArrayList<>();
        service.subscribe(ALICE, alice::add);
        service.subscribe(BOB, bob::add);

        int delivered = service.notifyEvent(1L, "for-alice");

        assertThat(alice).containsExactly("for-alice");
        assertThat(bob).isEmpty();
        assertThat(delivered).isEqualTo(1);
    }

    @Test
    void adminReceivesEverything() {
        List<String> admin = new ArrayList<>();
        service.subscribe(ADMIN, admin::add);

        service.notifyEvent(1L, "a");
        service.notifyEvent(2L, "b");
        service.notifyEvent(null, "legacy");

        assertThat(admin).containsExactly("a", "b", "legacy");
    }

    @Test
    void legacyTodoWithoutOwnerReachesOnlyAdmins() {
        List<String> alice = new ArrayList<>();
        List<String> admin = new ArrayList<>();
        service.subscribe(ALICE, alice::add);
        service.subscribe(ADMIN, admin::add);

        service.notifyEvent(null, "legacy");

        assertThat(alice).isEmpty();
        assertThat(admin).containsExactly("legacy");
    }

    @Test
    void adminWhoOwnsTheTodoGetsItExactlyOnce() {
        List<String> admin = new ArrayList<>();
        service.subscribe(ADMIN, admin::add);

        int delivered = service.notifyEvent(ADMIN.userId(), "mine");

        assertThat(admin).containsExactly("mine");
        assertThat(delivered).isEqualTo(1);
    }

    @Test
    void allConnectionsOfTheSameUserReceiveTheEvent() {
        List<String> tab1 = new ArrayList<>();
        List<String> tab2 = new ArrayList<>();
        service.subscribe(ALICE, tab1::add);
        service.subscribe(ALICE, tab2::add);

        service.notifyEvent(1L, "x");

        assertThat(tab1).containsExactly("x");
        assertThat(tab2).containsExactly("x");
    }

    @Test
    void closedSubscriptionStopsReceivingAndIsRemoved() {
        List<String> received = new ArrayList<>();
        Subscription subscription = service.subscribe(ALICE, received::add);

        subscription.close();
        subscription.close(); // gọi hai lần không sao
        service.notifyEvent(1L, "after-close");

        assertThat(received).isEmpty();
        assertThat(service.connectionCount()).isZero();
    }

    @Test
    void perUserLimitIsEnforcedAndFreesUpOnClose() {
        Subscription first = service.subscribe(ALICE, s -> { });
        service.subscribe(ALICE, s -> { });

        assertThatThrownBy(() -> service.subscribe(ALICE, s -> { }))
                .isInstanceOf(TooManySubscriptionsException.class);
        // người khác không bị ảnh hưởng
        service.subscribe(BOB, s -> { });

        first.close();
        service.subscribe(ALICE, s -> { });
        assertThat(service.connectionCount()).isEqualTo(3);
    }

    @Test
    void failingSinkIsDroppedWithoutAffectingOthers() {
        List<String> healthy = new ArrayList<>();
        service.subscribe(ALICE, s -> {
            throw new IllegalStateException("slow consumer");
        });
        service.subscribe(ALICE, healthy::add);

        int first = service.notifyEvent(1L, "one");
        int second = service.notifyEvent(1L, "two");

        assertThat(healthy).containsExactly("one", "two");
        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(1);
        assertThat(service.connectionCount()).isEqualTo(1); // sink lỗi đã bị gỡ
    }

    @Test
    void failingAdminSinkIsRemovedFromBothIndexes() {
        service.subscribe(ADMIN, s -> {
            throw new IllegalStateException("gone");
        });

        service.notifyEvent(1L, "x");

        assertThat(service.connectionCount()).isZero();
        assertThat(service.notifyEvent(1L, "y")).isZero();
    }

    @Test
    void notifyWithNobodyConnectedDeliversToNoOne() {
        assertThat(service.notifyEvent(1L, "x")).isZero();
    }

    @Test
    void limitMustBePositive() {
        assertThatThrownBy(() -> new RealtimeService(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void concurrentSubscribeNeverExceedsTheLimit() throws Exception {
        RealtimeService big = new RealtimeService(5);
        int threads = 32;
        java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger accepted = new java.util.concurrent.atomic.AtomicInteger();
        List<Thread> pool = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Thread t = new Thread(() -> {
                try {
                    go.await();
                    big.subscribe(ALICE, s -> { });
                    accepted.incrementAndGet();
                } catch (TooManySubscriptionsException | InterruptedException ignored) {
                    // bị từ chối là đúng
                }
            });
            pool.add(t);
            t.start();
        }
        go.countDown();
        for (Thread t : pool) {
            t.join();
        }

        assertThat(accepted.get()).isEqualTo(5);
        assertThat(big.connectionCount()).isEqualTo(5);
    }
}
