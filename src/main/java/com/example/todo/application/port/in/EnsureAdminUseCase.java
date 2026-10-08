package com.example.todo.application.port.in;

public interface EnsureAdminUseCase {

    /**
     * Tạo tài khoản ADMIN đầu tiên nếu email đó chưa tồn tại. Nếu đã tồn tại thì không làm gì
     * (không đổi mật khẩu, không đổi role). Dùng lúc khởi động ứng dụng.
     */
    void ensureAdmin(String email, String rawPassword);
}
