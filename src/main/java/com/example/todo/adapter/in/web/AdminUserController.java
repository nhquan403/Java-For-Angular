package com.example.todo.adapter.in.web;

import com.example.todo.adapter.in.web.dto.ChangeRoleRequest;
import com.example.todo.adapter.in.web.dto.PageResponse;
import com.example.todo.adapter.in.web.dto.UserResponse;
import com.example.todo.application.port.in.ChangeUserRoleUseCase;
import com.example.todo.application.port.in.ListUsersUseCase;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * INBOUND ADAPTER: quản lý người dùng, chỉ role ADMIN.
 * Có HAI lớp bảo vệ: SecurityConfig chặn /api/admin/** (trả 403) và use case tự kiểm tra lại.
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final ListUsersUseCase listUsers;
    private final ChangeUserRoleUseCase changeUserRole;

    public AdminUserController(ListUsersUseCase listUsers, ChangeUserRoleUseCase changeUserRole) {
        this.listUsers = listUsers;
        this.changeUserRole = changeUserRole;
    }

    @GetMapping
    public PageResponse<UserResponse> list(Authentication authentication,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(
                listUsers.list(AuthenticatedActor.from(authentication), page, size),
                UserResponse::from);
    }

    @PatchMapping("/{id}/role")
    public UserResponse changeRole(Authentication authentication,
                                   @PathVariable Long id,
                                   @Valid @RequestBody ChangeRoleRequest request) {
        return UserResponse.from(
                changeUserRole.changeRole(AuthenticatedActor.from(authentication), id, request.role()));
    }
}
