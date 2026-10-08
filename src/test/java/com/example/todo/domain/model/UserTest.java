package com.example.todo.domain.model;

import com.example.todo.domain.exception.InvalidUserException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    private static final Instant NOW = Instant.parse("2026-10-06T10:00:00Z");

    @Test
    void createNormalizesEmail() {
        User user = User.create("  Alice@Example.COM ", "hash", Role.USER, NOW);

        assertThat(user.email()).isEqualTo("alice@example.com");
        assertThat(user.id()).isNull();
    }

    @Test
    void invalidEmailsAreRejected() {
        for (String bad : new String[]{null, "", "   ", "no-at-sign", "a@b", "a b@c.com", "@x.com"}) {
            assertThatThrownBy(() -> User.validEmail(bad))
                    .as("email: %s", bad)
                    .isInstanceOf(InvalidUserException.class);
        }
    }

    @Test
    void passwordMustBeAtLeastEightCharacters() {
        assertThatThrownBy(() -> User.validateRawPassword("short"))
                .isInstanceOf(InvalidUserException.class);
        assertThatThrownBy(() -> User.validateRawPassword(null))
                .isInstanceOf(InvalidUserException.class);
        User.validateRawPassword("12345678");
    }

    @Test
    void passwordLongerThanBcryptLimitIsRejected() {
        assertThatThrownBy(() -> User.validateRawPassword("a".repeat(User.MAX_PASSWORD_BYTES + 1)))
                .isInstanceOf(InvalidUserException.class);
        // 25 ký tự có dấu = 75 byte UTF-8: vượt giới hạn dù chưa tới 72 ký tự
        assertThatThrownBy(() -> User.validateRawPassword("ệ".repeat(25)))
                .isInstanceOf(InvalidUserException.class);
        User.validateRawPassword("a".repeat(User.MAX_PASSWORD_BYTES));
    }

    @Test
    void withRoleReturnsNewInstance() {
        User user = User.create("a@b.com", "hash", Role.USER, NOW);

        User admin = user.withRole(Role.ADMIN);

        assertThat(user.role()).isEqualTo(Role.USER);
        assertThat(admin.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void toStringDoesNotLeakPasswordHash() {
        User user = User.create("a@b.com", "super-secret-hash", Role.USER, NOW);

        assertThat(user.toString()).doesNotContain("super-secret-hash");
    }
}
