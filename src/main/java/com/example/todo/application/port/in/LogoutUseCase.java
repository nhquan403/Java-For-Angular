package com.example.todo.application.port.in;

public interface LogoutUseCase {

    /** Thu hồi refresh token. Gọi nhiều lần hoặc với token lạ đều không lỗi (idempotent). */
    void logout(String refreshToken);
}
