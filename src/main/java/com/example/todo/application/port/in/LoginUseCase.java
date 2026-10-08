package com.example.todo.application.port.in;

import com.example.todo.application.common.AuthTokens;

public interface LoginUseCase {

    /** @throws com.example.todo.domain.exception.InvalidCredentialsException sai email hoặc mật khẩu */
    AuthTokens login(Command command);

    record Command(String email, String password) {

        /** Không in mật khẩu ra log. */
        @Override
        public String toString() {
            return "LoginUseCase.Command[email=" + email + ", password=***]";
        }
    }
}
