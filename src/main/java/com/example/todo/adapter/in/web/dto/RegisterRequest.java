package com.example.todo.adapter.in.web.dto;

import com.example.todo.domain.model.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "email must not be blank")
        @Email(message = "email is not valid")
        @Size(max = User.MAX_EMAIL_LENGTH, message = "email must not exceed {max} characters")
        String email,

        @NotBlank(message = "password must not be blank")
        @Size(min = User.MIN_PASSWORD_LENGTH, max = User.MAX_PASSWORD_BYTES,
                message = "password must be between {min} and {max} characters")
        String password) {

    /** Không in mật khẩu ra log. */
    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", password=***]";
    }
}
