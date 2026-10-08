package com.example.todo.application.port.in;

import com.example.todo.application.common.AuthTokens;

public interface RefreshTokenUseCase {

    /**
     * Đổi refresh token lấy cặp token mới. Refresh token cũ bị thu hồi ngay (dùng một lần).
     *
     * @throws com.example.todo.domain.exception.InvalidRefreshTokenException token sai, hết hạn,
     *                                                                        đã thu hồi hoặc đã dùng rồi
     */
    AuthTokens refresh(String refreshToken);
}
