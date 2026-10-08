package com.example.todo.application.port.out;

/** OUTBOUND PORT: băm và kiểm tra mật khẩu. Hiện thực thật dùng BCrypt. */
public interface PasswordHasherPort {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String hash);
}
