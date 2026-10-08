package com.example.todo.application.service;

import com.example.todo.application.common.AccessToken;
import com.example.todo.application.port.out.PasswordHasherPort;
import com.example.todo.application.port.out.TokenPort;
import com.example.todo.domain.model.User;

import java.time.Duration;
import java.time.Instant;

/** Các cổng bảo mật giả: nhanh, dễ đoán, chỉ dùng cho test lõi. */
final class FakeSecurityPorts {

    private FakeSecurityPorts() {
    }

    /** "Băm" bằng cách thêm tiền tố. KHÔNG an toàn, chỉ để test. */
    static class FakeHasher implements PasswordHasherPort {
        @Override
        public String hash(String rawPassword) {
            return "hashed:" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String hash) {
            return hash.equals("hashed:" + rawPassword);
        }
    }

    /** Access token giả "access-<userId>-<role>", refresh token tăng dần "refresh-1", "refresh-2"... */
    static class FakeTokens implements TokenPort {
        private int counter = 0;

        @Override
        public AccessToken issueAccessToken(User user, Instant now) {
            return new AccessToken("access-" + user.id() + "-" + user.role(), now.plus(Duration.ofMinutes(15)));
        }

        @Override
        public String newRefreshTokenValue() {
            return "refresh-" + (++counter);
        }

        @Override
        public String hashRefreshToken(String rawRefreshToken) {
            return "h(" + rawRefreshToken + ")";
        }
    }
}
