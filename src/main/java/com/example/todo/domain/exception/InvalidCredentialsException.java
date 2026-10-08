package com.example.todo.domain.exception;

/**
 * Đăng nhập thất bại. Cố ý dùng MỘT thông báo chung cho cả "sai email" và "sai mật khẩu"
 * để kẻ tấn công không dò được email nào đã đăng ký.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
