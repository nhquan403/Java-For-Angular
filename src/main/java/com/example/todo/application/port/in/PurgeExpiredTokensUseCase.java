package com.example.todo.application.port.in;

public interface PurgeExpiredTokensUseCase {

    /** Xóa các refresh token đã hết hạn khỏi database. Trả về số token đã xóa. */
    int purgeExpired();
}
