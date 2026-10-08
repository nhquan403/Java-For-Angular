package com.example.todo.application.port.out;

import com.example.todo.domain.model.RefreshToken;

import java.time.Instant;
import java.util.Optional;

/** OUTBOUND PORT: kho lưu refresh token (chỉ lưu bản băm). */
public interface RefreshTokenRepositoryPort {

    RefreshToken save(RefreshToken token);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Thu hồi token NẾU nó đang còn hiệu lực, làm trong một câu lệnh nguyên tử.
     * Trả về true nếu lần gọi này là lần thu hồi thành công, false nếu token đã bị thu hồi từ trước.
     * Nhờ vậy hai request đồng thời dùng cùng một token thì chỉ một request thắng.
     */
    boolean revokeIfActive(Long tokenId, Instant now);

    /** Thu hồi mọi token còn hiệu lực của user. Trả về số token bị thu hồi. */
    int revokeAllActiveForUser(Long userId, Instant now);

    /** Xóa token đã hết hạn trước thời điểm cutoff. Trả về số token đã xóa. */
    int deleteExpiredBefore(Instant cutoff);
}
