package com.example.todo.application.service;

import com.example.todo.application.common.Actor;
import com.example.todo.application.common.PageResult;
import com.example.todo.domain.exception.ForbiddenOperationException;
import com.example.todo.application.common.InvalidPageQueryException;
import com.example.todo.domain.exception.InvalidUserException;
import com.example.todo.domain.exception.UserNotFoundException;
import com.example.todo.domain.model.RefreshToken;
import com.example.todo.domain.model.Role;
import com.example.todo.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-06T10:00:00Z");

    private InMemoryUserRepository users;
    private InMemoryRefreshTokenRepository refreshTokens;
    private UserService service;

    private User admin;
    private User alice;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserRepository();
        refreshTokens = new InMemoryRefreshTokenRepository();
        service = new UserService(users, refreshTokens, new MutableClock(NOW));
        admin = users.save(User.create("admin@example.com", "h", Role.ADMIN, NOW));
        alice = users.save(User.create("alice@example.com", "h", Role.USER, NOW));
    }

    private Actor adminActor() {
        return Actor.admin(admin.id());
    }

    @Test
    void adminCanListUsers() {
        PageResult<User> page = service.list(adminActor(), 0, 10);

        assertThat(page.content()).extracting(User::email)
                .containsExactly("admin@example.com", "alice@example.com");
    }

    @Test
    void regularUserCannotListUsers() {
        assertThatThrownBy(() -> service.list(Actor.user(alice.id()), 0, 10))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void listValidatesPaging() {
        assertThatThrownBy(() -> service.list(adminActor(), -1, 10))
                .isInstanceOf(InvalidPageQueryException.class);
        assertThatThrownBy(() -> service.list(adminActor(), 0, 100000))
                .isInstanceOf(InvalidPageQueryException.class);
    }

    @Test
    void getByIdThrowsWhenMissing() {
        assertThat(service.getById(alice.id()).email()).isEqualTo("alice@example.com");
        assertThatThrownBy(() -> service.getById(999L)).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void adminCanPromoteAUserAndTheirRefreshTokensAreRevoked() {
        refreshTokens.save(RefreshToken.issue(alice.id(), "h1", NOW, Duration.ofDays(7)));
        refreshTokens.save(RefreshToken.issue(alice.id(), "h2", NOW, Duration.ofDays(7)));

        User updated = service.changeRole(adminActor(), alice.id(), Role.ADMIN);

        assertThat(updated.role()).isEqualTo(Role.ADMIN);
        assertThat(users.findById(alice.id()).orElseThrow().role()).isEqualTo(Role.ADMIN);
        // buộc đăng nhập lại để nhận token mang role mới
        assertThat(refreshTokens.activeCount(alice.id())).isZero();
    }

    @Test
    void changingToTheSameRoleChangesNothing() {
        refreshTokens.save(RefreshToken.issue(alice.id(), "h1", NOW, Duration.ofDays(7)));

        service.changeRole(adminActor(), alice.id(), Role.USER);

        assertThat(refreshTokens.activeCount(alice.id())).isEqualTo(1);
    }

    @Test
    void regularUserCannotChangeRoles() {
        assertThatThrownBy(() -> service.changeRole(Actor.user(alice.id()), alice.id(), Role.ADMIN))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThat(users.findById(alice.id()).orElseThrow().role()).isEqualTo(Role.USER);
    }

    @Test
    void adminCannotChangeTheirOwnRole() {
        assertThatThrownBy(() -> service.changeRole(adminActor(), admin.id(), Role.USER))
                .isInstanceOf(InvalidUserException.class);
        assertThat(users.findById(admin.id()).orElseThrow().role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void changeRoleOfMissingUserFails() {
        assertThatThrownBy(() -> service.changeRole(adminActor(), 999L, Role.ADMIN))
                .isInstanceOf(UserNotFoundException.class);
    }
}
