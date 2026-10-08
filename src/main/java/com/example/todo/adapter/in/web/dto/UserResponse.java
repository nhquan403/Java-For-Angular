package com.example.todo.adapter.in.web.dto;

import com.example.todo.domain.model.Role;
import com.example.todo.domain.model.User;

import java.time.Instant;

/** Thông tin user trả cho client. Không bao giờ có mật khẩu hay mã băm mật khẩu. */
public record UserResponse(Long id, String email, Role role, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.id(), user.email(), user.role(), user.createdAt());
    }
}
