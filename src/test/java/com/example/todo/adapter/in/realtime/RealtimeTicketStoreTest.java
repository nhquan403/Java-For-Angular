package com.example.todo.adapter.in.realtime;

import com.example.todo.application.common.Actor;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class RealtimeTicketStoreTest {

    private static final Actor ALICE = Actor.user(1L);

    /** Đồng hồ giả tua được. */
    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-10-06T10:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final TestClock clock = new TestClock();
    private final RealtimeTicketStore store = new RealtimeTicketStore(clock, Duration.ofSeconds(30), 3);

    @Test
    void ticketWorksExactlyOnce() {
        String ticket = store.issue(ALICE).orElseThrow();

        assertThat(store.consume(ticket)).isEqualTo(java.util.Optional.of(ALICE));
        assertThat(store.consume(ticket).isPresent()).isFalse();
    }

    @Test
    void expiredTicketIsRejected() {
        String ticket = store.issue(ALICE).orElseThrow();

        clock.advance(Duration.ofSeconds(30));

        assertThat(store.consume(ticket).isPresent()).isFalse();
    }

    @Test
    void ticketJustBeforeExpiryStillWorks() {
        String ticket = store.issue(ALICE).orElseThrow();

        clock.advance(Duration.ofSeconds(29));

        assertThat(store.consume(ticket).isPresent()).isTrue();
    }

    @Test
    void unknownOrNullTicketIsRejected() {
        assertThat(store.consume("nope").isPresent()).isFalse();
        assertThat(store.consume(null).isPresent()).isFalse();
    }

    @Test
    void ticketsAreUniqueAndLongEnoughToBeUnguessable() {
        String a = store.issue(ALICE).orElseThrow();
        String b = store.issue(ALICE).orElseThrow();

        assertThat(a).isNotEqualTo(b);
        assertThat(a.length()).isEqualTo(43); // 32 byte base64url không đệm
    }

    @Test
    void ticketOfOneUserNeverYieldsAnotherUser() {
        String ticket = store.issue(Actor.admin(7L)).orElseThrow();

        Actor actor = store.consume(ticket).orElseThrow();

        assertThat(actor.userId()).isEqualTo(7L);
        assertThat(actor.admin()).isTrue();
    }

    @Test
    void refusesToIssueWhenTooManyAreWaitingButRecoversAfterExpiry() {
        store.issue(ALICE);
        store.issue(ALICE);
        store.issue(ALICE);

        assertThat(store.issue(ALICE).isPresent()).isFalse();

        clock.advance(Duration.ofSeconds(31)); // cả ba hết hạn, được dọn khi cần chỗ
        assertThat(store.issue(ALICE).isPresent()).isTrue();
    }

    @Test
    void purgeRemovesOnlyExpiredTickets() {
        store.issue(ALICE);
        clock.advance(Duration.ofSeconds(20));
        store.issue(ALICE);
        clock.advance(Duration.ofSeconds(15)); // vé đầu 35 giây tuổi, vé sau 15 giây

        store.purgeExpired();

        assertThat(store.pendingCount()).isEqualTo(1);
    }
}
