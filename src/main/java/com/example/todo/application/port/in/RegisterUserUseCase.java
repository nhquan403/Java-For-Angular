package com.example.todo.application.port.in;

import com.example.todo.domain.model.User;

public interface RegisterUserUseCase {

    /**
     * Đăng ký tài khoản mới với role USER.
     *
     * @throws com.example.todo.domain.exception.EmailAlreadyUsedException email đã có người dùng
     * @throws com.example.todo.domain.exception.InvalidUserException      email hoặc mật khẩu không hợp lệ
     */
    User register(Command command);

    record Command(String email, String password) {

        /** Không in mật khẩu ra log. */
        @Override
        public String toString() {
            return "RegisterUserUseCase.Command[email=" + email + ", password=***]";
        }
    }
}
