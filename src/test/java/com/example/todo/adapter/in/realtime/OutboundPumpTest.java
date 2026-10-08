package com.example.todo.adapter.in.realtime;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboundPumpTest {

    /** Kênh giả ghi lại những gì được gửi. */
    private static class FakeChannel implements OutboundPump.Channel {
        final List<String> sent = new CopyOnWriteArrayList<>();
        final AtomicInteger heartbeats = new AtomicInteger();
        final CountDownLatch closed = new CountDownLatch(1);
        volatile boolean failOnSend;
        volatile CountDownLatch blockSend;

        @Override
        public void send(String payload) throws Exception {
            if (blockSend != null) {
                blockSend.await();
            }
            if (failOnSend) {
                throw new IOException("client gone");
            }
            sent.add(payload);
        }

        @Override
        public void heartbeat() {
            heartbeats.incrementAndGet();
        }

        @Override
        public void close() {
            closed.countDown();
        }
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("condition not met within 5s");
            }
            Thread.sleep(5);
        }
    }

    @Test
    void messagesAreSentInOrder() throws Exception {
        FakeChannel channel = new FakeChannel();
        OutboundPump pump = new OutboundPump(channel, 16, Duration.ofSeconds(10), Duration.ofMinutes(1));
        pump.start();

        pump.offer("a");
        pump.offer("b");
        pump.offer("c");

        waitUntil(() -> channel.sent.size() == 3);
        assertThat(channel.sent).containsExactly("a", "b", "c");
        pump.close();
    }

    @Test
    void sendsHeartbeatWhenIdle() throws Exception {
        FakeChannel channel = new FakeChannel();
        OutboundPump pump = new OutboundPump(channel, 16, Duration.ofMillis(20), Duration.ofMinutes(1));
        pump.start();

        waitUntil(() -> channel.heartbeats.get() >= 2);
        pump.close();
    }

    @Test
    void closesItselfWhenMaxAgeIsReached() throws Exception {
        FakeChannel channel = new FakeChannel();
        OutboundPump pump = new OutboundPump(channel, 16, Duration.ofMillis(10), Duration.ofMillis(60));
        pump.start();

        assertThat(channel.closed.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(pump.isClosed()).isTrue();
    }

    @Test
    void slowConsumerIsDisconnectedInsteadOfBufferingForever() throws Exception {
        FakeChannel channel = new FakeChannel();
        channel.blockSend = new CountDownLatch(1); // client "đứng hình", không nhận được gì
        AtomicInteger closedCalls = new AtomicInteger();
        OutboundPump pump = new OutboundPump(channel, 2, Duration.ofSeconds(10), Duration.ofMinutes(1));
        pump.onClose(closedCalls::incrementAndGet);
        pump.start();

        pump.offer("1"); // luồng ghi lấy ra rồi kẹt ở send
        Thread.sleep(50);
        pump.offer("2");
        pump.offer("3"); // hàng đợi (2) đầy
        assertThatThrownBy(() -> pump.offer("4")).isInstanceOf(IllegalStateException.class);

        assertThat(pump.isClosed()).isTrue();
        assertThat(closedCalls.get()).isEqualTo(1);
        assertThat(channel.closed.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void offerNeverBlocksTheCaller() throws Exception {
        FakeChannel channel = new FakeChannel();
        channel.blockSend = new CountDownLatch(1);
        OutboundPump pump = new OutboundPump(channel, 100, Duration.ofSeconds(10), Duration.ofMinutes(1));
        pump.start();

        long start = System.nanoTime();
        for (int i = 0; i < 50; i++) {
            pump.offer("m" + i); // kênh bị kẹt hoàn toàn mà các lần offer vẫn xong ngay
        }
        long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertThat(millis < 1000).isTrue();
        channel.blockSend.countDown();
        pump.close();
    }

    @Test
    void sendFailureClosesThePumpAndRunsOnCloseOnce() throws Exception {
        FakeChannel channel = new FakeChannel();
        channel.failOnSend = true;
        AtomicInteger closedCalls = new AtomicInteger();
        OutboundPump pump = new OutboundPump(channel, 16, Duration.ofSeconds(10), Duration.ofMinutes(1));
        pump.onClose(closedCalls::incrementAndGet);
        pump.start();

        pump.offer("x");

        assertThat(channel.closed.await(5, TimeUnit.SECONDS)).isTrue();
        pump.close();
        assertThat(closedCalls.get()).isEqualTo(1);
    }

    @Test
    void offerAfterCloseFails() {
        FakeChannel channel = new FakeChannel();
        OutboundPump pump = new OutboundPump(channel, 16, Duration.ofSeconds(10), Duration.ofMinutes(1));
        pump.close();

        assertThatThrownBy(() -> pump.offer("late")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void onCloseRunsImmediatelyIfAlreadyClosed() {
        FakeChannel channel = new FakeChannel();
        OutboundPump pump = new OutboundPump(channel, 16, Duration.ofSeconds(10), Duration.ofMinutes(1));
        pump.close();
        AtomicInteger calls = new AtomicInteger();

        pump.onClose(calls::incrementAndGet);

        assertThat(calls.get()).isEqualTo(1);
    }
}
