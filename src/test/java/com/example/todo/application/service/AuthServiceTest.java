package com.example.todo.application.service;

import com.example.todo.application.common.AuthTokens;
import com.example.todo.application.port.in.LoginUseCase;
import com.example.todo.application.port.in.RegisterUserUseCase;
import com.example.todo.domain.exception.EmailAlreadyUsedException;
import com.example.todo.domain.exception.InvalidCredentialsException;
import com.example.todo.domain.exception.InvalidRefreshTokenException;
import com.example.todo.domain.exception.InvalidUserException;
import com.example.todo.domain.model.Role;
import com.example.todo.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthServiceTest {

    private static final Instant START = Instant.parse("2026-10-06T10:00:00Z");
    private static final Duration REFRESH_TTL = Duration.ofDays(7);

    private InMemoryUserRepository users;
    private InMemoryRefreshTokenRepository refreshTokens;
    private MutableClock clock;
    private AuthService service;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserRepository();
        refreshTokens = new InMemoryRefreshTokenRepository();
        clock = new MutableClock(START);
        service = new AuthService(users, refreshTokens, new FakeSecurityPorts.FakeHasher(),
                new FakeSecurityPorts.FakeTokens(), clock, REFRESH_TTL);
    }

    private User register(String email, String password) {
        return service.register(new RegisterUserUseCase.Command(email, password));
    }

    private AuthTokens login(String email, String password) {
        return service.login(new LoginUseCase.Command(email, password));
    }

    // ---- Đăng ký ----

    @Test
    void registerCreatesUserWithRoleUserAndHashedPassword() {
        User user = register("Alice@Example.com", "password123");

        assertThat(user.id()).isNotNull();
        assertThat(user.email()).isEqualTo("alice@example.com");
        assertThat(user.role()).isEqualTo(Role.USER);
        assertThat(user.passwordHash()).isNotEqualTo("password123");
    }

    @Test
    void registerRejectsDuplicateEmailIgnoringCase() {
        register("alice@example.com", "password123");

        assertThatThrownBy(() -> register("ALICE@example.com", "another-pass"))
                .isInstanceOf(EmailAlreadyUsedException.class);
    }

    @Test
    void registerRejectsBadEmailAndShortPassword() {
        assertThatThrownBy(() -> register("not-an-email", "password123"))
                .isInstanceOf(InvalidUserException.class);
        assertThatThrownBy(() -> register("a@b.com", "short"))
                .isInstanceOf(InvalidUserException.class);
    }

    // ---- Đăng nhập ----

    @Test
    void loginReturnsAccessAndRefreshTokens() {
        User user = register("alice@example.com", "password123");

        AuthTokens tokens = login("alice@example.com", "password123");

        assertThat(tokens.accessToken()).isEqualTo("access-" + user.id() + "-USER");
        assertThat(tokens.accessTokenExpiresAt()).isEqualTo(START.plus(Duration.ofMinutes(15)));
        assertThat(tokens.refreshToken()).isNotBlank();
        assertThat(tokens.refreshTokenExpiresAt()).isEqualTo(START.plus(REFRESH_TTL));
        assertThat(refreshTokens.activeCount(user.id())).isEqualTo(1);
    }

    @Test
    void loginIgnoresEmailCaseAndSurroundingSpaces() {
        register("alice@example.com", "password123");

        assertThat(login("  ALICE@Example.com ", "password123").accessToken()).isNotBlank();
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameError() {
        register("alice@example.com", "password123");

        assertThatThrownBy(() -> login("alice@example.com", "wrong-password"))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> login("ghost@example.com", "password123"))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> login(null, null))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void failedLoginDoesNotIssueAnyToken() {
        User user = register("alice@example.com", "password123");

        assertThatThrownBy(() -> login("alice@example.com", "nope"))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(refreshTokens.activeCount(user.id())).isZero();
    }

    // ---- Làm mới token ----

    @Test
    void refreshRotatesTheToken() {
        User user = register("alice@example.com", "password123");
        AuthTokens first = login("alice@example.com", "password123");
        clock.advance(Duration.ofMinutes(5));

        AuthTokens second = service.refresh(first.refreshToken());

        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(second.accessTokenExpiresAt()).isAfter(first.accessTokenExpiresAt());
        // token cũ đã bị thu hồi, chỉ còn token mới
        assertThat(refreshTokens.activeCount(user.id())).isEqualTo(1);
    }

    @Test
    void reusingAnOldRefreshTokenRevokesEverything() {
        User user = register("alice@example.com", "password123");
        AuthTokens first = login("alice@example.com", "password123");
        AuthTokens second = service.refresh(first.refreshToken());

        // Kẻ cắp (hoặc client lỗi) đem token cũ ra dùng lại
        assertThatThrownBy(() -> service.refresh(first.refreshToken()))
                .isInstanceOf(InvalidRefreshTokenException.class);

        // Token mới của người dùng thật cũng bị thu hồi, buộc đăng nhập lại
        assertThat(refreshTokens.activeCount(user.id())).isZero();
        assertThatThrownBy(() -> service.refresh(second.refreshToken()))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void expiredRefreshTokenIsRejected() {
        register("alice@example.com", "password123");
        AuthTokens tokens = login("alice@example.com", "password123");

        clock.advance(REFRESH_TTL.plusSeconds(1));

        assertThatThrownBy(() -> service.refresh(tokens.refreshToken()))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void unknownBlankOrNullRefreshTokenIsRejected() {
        assertThatThrownBy(() -> service.refresh("made-up-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> service.refresh("  "))
                .isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> service.refresh(null))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refreshReflectsTheCurrentRoleOfTheUser() {
        User user = register("alice@example.com", "password123");
        AuthTokens tokens = login("alice@example.com", "password123");
        users.save(user.withRole(Role.ADMIN));

        AuthTokens refreshed = service.refresh(tokens.refreshToken());

        assertThat(refreshed.accessToken()).isEqualTo("access-" + user.id() + "-ADMIN");
    }

    // ---- Đăng xuất ----

    @Test
    void logoutRevokesTheRefreshToken() {
        register("alice@example.com", "password123");
        AuthTokens tokens = login("alice@example.com", "password123");

        service.logout(tokens.refreshToken());

        assertThatThrownBy(() -> service.refresh(tokens.refreshToken()))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void logoutWithGarbageIsSilentlyIgnored() {
        service.logout("made-up-token");
        service.logout(null);
        service.logout("");
    }

    // ---- ADMIN khởi tạo ----

    @Test
    void ensureAdminCreatesAnAdminOnce() {
        service.ensureAdmin("Admin@Example.com", "admin-pass-1");
        service.ensureAdmin("admin@example.com", "a-different-password");

        User admin = users.findByEmail("admin@example.com").orElseThrow();
        assertThat(admin.role()).isEqualTo(Role.ADMIN);
        // lần gọi thứ hai không ghi đè mật khẩu
        assertThat(login("admin@example.com", "admin-pass-1").accessToken()).isNotBlank();
        assertThatThrownBy(() -> login("admin@example.com", "a-different-password"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void ensureAdminDoesNotPromoteAnExistingUser() {
        register("alice@example.com", "password123");

        service.ensureAdmin("alice@example.com", "whatever-pass");

        assertThat(users.findByEmail("alice@example.com").orElseThrow().role()).isEqualTo(Role.USER);
    }

    @Test
    void ensureAdminRejectsWeakPassword() {
        assertThatThrownBy(() -> service.ensureAdmin("admin@example.com", "123"))
                .isInstanceOf(InvalidUserException.class);
    }

    // ---- Dọn token hết hạn ----

    @Test
    void purgeExpiredRemovesOnlyExpiredTokens() {
        register("alice@example.com", "password123");
        login("alice@example.com", "password123");
        clock.advance(Duration.ofDays(6));
        login("alice@example.com", "password123");
        assertThat(refreshTokens.size()).isEqualTo(2);

        clock.advance(Duration.ofDays(2)); // token đầu (7 ngày) hết hạn, token sau thì chưa

        assertThat(service.purgeExpired()).isEqualTo(1);
        assertThat(refreshTokens.size()).isEqualTo(1);
    }

    @Test
    void secretsAreNotPrintedByToString() {
        register("alice@example.com", "password123");
        AuthTokens tokens = login("alice@example.com", "password123");

        assertThat(tokens.toString()).doesNotContain(tokens.accessToken()).doesNotContain(tokens.refreshToken());
        assertThat(new LoginUseCase.Command("a@b.com", "password123").toString()).doesNotContain("password123");
        assertThat(new RegisterUserUseCase.Command("a@b.com", "password123").toString())
                .doesNotContain("password123");
    }
}
