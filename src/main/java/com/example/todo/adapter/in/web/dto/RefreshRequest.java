package com.example.todo.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

/** Dùng chung cho làm mới token và đăng xuất. */
public record RefreshRequest(
        @NotBlank(message = "refreshToken must not be blank")
        String refreshToken) {

    /** Không in token ra log. */
    @Override
    public String toString() {
        return "RefreshRequest[refreshToken=***]";
    }
}
