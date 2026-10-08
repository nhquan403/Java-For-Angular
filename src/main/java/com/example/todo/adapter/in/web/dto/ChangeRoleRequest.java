package com.example.todo.adapter.in.web.dto;

import com.example.todo.domain.model.Role;
import jakarta.validation.constraints.NotNull;

/** Giá trị hợp lệ: "USER" hoặc "ADMIN". Giá trị khác trả 400. */
public record ChangeRoleRequest(
        @NotNull(message = "role must not be null")
        Role role) {
}
