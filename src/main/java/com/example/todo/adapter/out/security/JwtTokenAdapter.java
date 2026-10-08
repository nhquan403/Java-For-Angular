package com.example.todo.adapter.out.security;

import com.example.todo.application.common.AccessToken;
import com.example.todo.application.port.out.TokenPort;
import com.example.todo.domain.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * OUTBOUND ADAPTER: hiện thực TokenPort.
 * - Access token là JWT ký HS256 (xem JwtConfig), sống ngắn, chứa id, email và role.
 * - Refresh token là chuỗi ngẫu nhiên 256 bit KHÔNG phải JWT. Nó chỉ là "chìa khóa" tra cứu trong
 *   database nên không cần tự mang thông tin, và thu hồi được ngay.
 */
@Component
class JwtTokenAdapter implements TokenPort {

    private static final int REFRESH_TOKEN_BYTES = 32;

    private final JwtEncoder encoder;
    private final String issuer;
    private final Duration accessTokenTtl;
    private final SecureRandom random = new SecureRandom();

    JwtTokenAdapter(JwtEncoder encoder,
                    @Value("${app.security.jwt.issuer}") String issuer,
                    @Value("${app.security.jwt.access-token-ttl}") Duration accessTokenTtl) {
        this.encoder = encoder;
        this.issuer = issuer;
        this.accessTokenTtl = accessTokenTtl;
    }

    @Override
    public AccessToken issueAccessToken(User user, Instant now) {
        Instant expiresAt = now.plus(accessTokenTtl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(String.valueOf(user.id()))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("email", user.email())
                .claim("roles", List.of(user.role().name()))
                .build();
        // Phải nêu rõ thuật toán HS256, nếu không encoder mặc định tìm khóa RSA.
        // type("JWT") đặt rõ để header "typ" luôn đúng chuẩn RFC 7519.
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(value, expiresAt);
    }

    @Override
    public String newRefreshTokenValue() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256 là đủ cho refresh token vì nó đã là chuỗi ngẫu nhiên 256 bit, không đoán được.
     * (Mật khẩu của người dùng thì khác: ít ngẫu nhiên nên phải dùng BCrypt chậm.)
     */
    @Override
    public String hashRefreshToken(String rawRefreshToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawRefreshToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
