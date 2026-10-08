package com.example.todo.application.service;

import com.example.todo.application.common.Actor;
import com.example.todo.application.common.PageQuery;
import com.example.todo.application.common.PageResult;
import com.example.todo.application.port.in.ChangeUserRoleUseCase;
import com.example.todo.application.port.in.GetUserUseCase;
import com.example.todo.application.port.in.ListUsersUseCase;
import com.example.todo.application.port.out.RefreshTokenRepositoryPort;
import com.example.todo.application.port.out.UserRepositoryPort;
import com.example.todo.domain.exception.ForbiddenOperationException;
import com.example.todo.domain.exception.InvalidUserException;
import com.example.todo.domain.exception.UserNotFoundException;
import com.example.todo.domain.model.Role;
import com.example.todo.domain.model.User;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;

/**
 * Xem và quản lý người dùng. Quyền ADMIN được kiểm tra Ở ĐÂY (lõi), không chỉ ở cấu hình URL,
 * nên dù sau này có adapter khác (CLI, gRPC) gọi vào thì quy tắc vẫn được giữ.
 */
@Transactional
public class UserService implements GetUserUseCase, ListUsersUseCase, ChangeUserRoleUseCase {

    private final UserRepositoryPort users;
    private final RefreshTokenRepositoryPort refreshTokens;
    private final Clock clock;

    public UserService(UserRepositoryPort users, RefreshTokenRepositoryPort refreshTokens, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public User getById(Long id) {
        return users.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<User> list(Actor actor, int page, int size) {
        requireAdmin(actor);
        // Dùng PageQuery chỉ để tái sử dụng quy tắc kiểm tra page và size.
        PageQuery query = PageQuery.of(page, size, "id", "asc");
        return users.findAll(query.page(), query.size());
    }

    @Override
    public User changeRole(Actor actor, Long userId, Role newRole) {
        requireAdmin(actor);
        Objects.requireNonNull(newRole, "newRole must not be null");
        if (actor.userId().equals(userId)) {
            // Tránh tự hạ quyền rồi hệ thống không còn ADMIN nào.
            throw new InvalidUserException("You cannot change your own role");
        }
        User user = users.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        if (user.role() == newRole) {
            return user;
        }
        User saved = users.save(user.withRole(newRole));
        // Access token cũ vẫn mang role cũ cho đến khi hết hạn. Thu hồi refresh token để
        // user phải đăng nhập lại và nhận token với role mới.
        refreshTokens.revokeAllActiveForUser(userId, clock.instant());
        return saved;
    }

    private static void requireAdmin(Actor actor) {
        if (!actor.admin()) {
            throw new ForbiddenOperationException("Only an administrator can do this");
        }
    }
}
