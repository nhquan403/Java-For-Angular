package com.example.todo.application.service;

import com.example.todo.application.common.AccessToken;
import com.example.todo.application.common.AuthTokens;
import com.example.todo.application.port.in.EnsureAdminUseCase;
import com.example.todo.application.port.in.LoginUseCase;
import com.example.todo.application.port.in.LogoutUseCase;
import com.example.todo.application.port.in.PurgeExpiredTokensUseCase;
import com.example.todo.application.port.in.RefreshTokenUseCase;
import com.example.todo.application.port.in.RegisterUserUseCase;
import com.example.todo.application.port.out.PasswordHasherPort;
import com.example.todo.application.port.out.RefreshTokenRepositoryPort;
import com.example.todo.application.port.out.TokenPort;
import com.example.todo.application.port.out.UserRepositoryPort;
import com.example.todo.domain.exception.EmailAlreadyUsedException;
import com.example.todo.domain.exception.InvalidCredentialsException;
import com.example.todo.domain.exception.InvalidRefreshTokenException;
import com.example.todo.domain.model.RefreshToken;
import com.example.todo.domain.model.Role;
import com.example.todo.domain.model.User;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Đăng ký, đăng nhập, làm mới và thu hồi token. Không biết JWT, BCrypt hay JPA,
 * chỉ biết các cổng ra (TokenPort, PasswordHasherPort, repository).
 */
@Transactional
public class AuthService implements
        RegisterUserUseCase,
        LoginUseCase,
        RefreshTokenUseCase,
        LogoutUseCase,
        EnsureAdminUseCase,
        PurgeExpiredTokensUseCase {

    private final UserRepositoryPort users;
    private final RefreshTokenRepositoryPort refreshTokens;
    private final PasswordHasherPort hasher;
    private final TokenPort tokens;
    private final Clock clock;
    private final Duration refreshTokenTtl;

    /**
     * Một mã băm "giả" hợp lệ. Khi email không tồn tại, ta vẫn so mật khẩu với mã này để thời gian
     * phản hồi gần bằng trường hợp email có thật, tránh việc đo thời gian để dò email đã đăng ký.
     */
    private final String dummyHash;

    public AuthService(UserRepositoryPort users,
                       RefreshTokenRepositoryPort refreshTokens,
                       PasswordHasherPort hasher,
                       TokenPort tokens,
                       Clock clock,
                       Duration refreshTokenTtl) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.hasher = hasher;
        this.tokens = tokens;
        this.clock = clock;
        this.refreshTokenTtl = Objects.requireNonNull(refreshTokenTtl, "refreshTokenTtl must not be null");
        this.dummyHash = hasher.hash("dummy-" + UUID.randomUUID());
    }

    @Override
    public User register(RegisterUserUseCase.Command command) {
        String email = User.validEmail(command.email());
        User.validateRawPassword(command.password());
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyUsedException();
        }
        User user = User.create(email, hasher.hash(command.password()), Role.USER, now());
        return users.save(user);
    }

    @Override
    public AuthTokens login(LoginUseCase.Command command) {
        String email = User.normalizeEmail(command.email());
        String rawPassword = command.password() == null ? "" : command.password();

        Optional<User> found = users.findByEmail(email);
        boolean passwordOk = hasher.matches(rawPassword, found.map(User::passwordHash).orElse(dummyHash));
        if (found.isEmpty() || !passwordOk) {
            throw new InvalidCredentialsException();
        }
        return issueTokens(found.get(), now());
    }

    /**
     * Xoay vòng refresh token:
     * 1. Token đã bị thu hồi mà vẫn được đem đến dùng nghĩa là có thể đã bị đánh cắp
     *    (kẻ cắp và người thật đều cầm bản sao). Thu hồi TOÀN BỘ token của user, buộc đăng nhập lại.
     * 2. Token hợp lệ thì thu hồi nó rồi phát cặp mới.
     *
     * noRollbackFor: lệnh thu hồi ở bước 1 phải được LƯU dù sau đó ném exception. Mặc định Spring
     * hoàn tác giao dịch khi có RuntimeException, và việc thu hồi sẽ mất.
     */
    @Override
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthTokens refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        Instant now = now();
        RefreshToken stored = refreshTokens.findByTokenHash(tokens.hashRefreshToken(refreshToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (stored.isRevoked()) {
            refreshTokens.revokeAllActiveForUser(stored.userId(), now);
            throw new InvalidRefreshTokenException();
        }
        if (stored.isExpired(now)) {
            throw new InvalidRefreshTokenException();
        }
        // Thu hồi nguyên tử: hai request đồng thời cùng một token thì chỉ một request thắng.
        if (!refreshTokens.revokeIfActive(stored.id(), now)) {
            refreshTokens.revokeAllActiveForUser(stored.userId(), now);
            throw new InvalidRefreshTokenException();
        }
        User user = users.findById(stored.userId()).orElseThrow(InvalidRefreshTokenException::new);
        return issueTokens(user, now);
    }

    @Override
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        refreshTokens.findByTokenHash(tokens.hashRefreshToken(refreshToken))
                .ifPresent(token -> refreshTokens.revokeIfActive(token.id(), now()));
    }

    @Override
    public void ensureAdmin(String email, String rawPassword) {
        String normalized = User.validEmail(email);
        if (users.existsByEmail(normalized)) {
            return;
        }
        User.validateRawPassword(rawPassword);
        users.save(User.create(normalized, hasher.hash(rawPassword), Role.ADMIN, now()));
    }

    @Override
    public int purgeExpired() {
        return refreshTokens.deleteExpiredBefore(now());
    }

    private AuthTokens issueTokens(User user, Instant now) {
        AccessToken access = tokens.issueAccessToken(user, now);
        String rawRefresh = tokens.newRefreshTokenValue();
        RefreshToken stored = refreshTokens.save(
                RefreshToken.issue(user.id(), tokens.hashRefreshToken(rawRefresh), now, refreshTokenTtl));
        return new AuthTokens(access.value(), access.expiresAt(), rawRefresh, stored.expiresAt());
    }

    private Instant now() {
        return clock.instant();
    }
}
