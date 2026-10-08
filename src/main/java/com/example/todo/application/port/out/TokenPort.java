package com.example.todo.application.port.out;

import com.example.todo.application.common.AccessToken;
import com.example.todo.domain.model.User;

import java.time.Instant;

/**
 * OUTBOUND PORT: tạo token. Lõi ứng dụng không biết access token là JWT hay thứ gì khác.
 */
public interface TokenPort {

    /** Phát access token ngắn hạn cho user, chứa id và role. */
    AccessToken issueAccessToken(User user, Instant now);

    /** Sinh một refresh token ngẫu nhiên, khó đoán. Đây là giá trị gốc gửi cho client. */
    String newRefreshTokenValue();

    /** Băm refresh token trước khi lưu hoặc tra cứu trong database. */
    String hashRefreshToken(String rawRefreshToken);
}
