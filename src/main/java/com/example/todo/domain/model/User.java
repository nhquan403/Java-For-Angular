package com.example.todo.domain.model;

import com.example.todo.domain.exception.InvalidUserException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Người dùng của hệ thống. Bất biến, không có annotation của Spring hay JPA.
 * Domain chỉ giữ mật khẩu đã băm (passwordHash), không bao giờ giữ mật khẩu gốc.
 */
public final class User {

    public static final int MAX_EMAIL_LENGTH = 254;
    public static final int MIN_PASSWORD_LENGTH = 8;
    /** BCrypt chỉ dùng 72 byte đầu của mật khẩu, nên từ chối mật khẩu dài hơn thay vì cắt âm thầm. */
    public static final int MAX_PASSWORD_BYTES = 72;

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final Long id;
    private final String email;
    private final String passwordHash;
    private final Role role;
    private final Instant createdAt;

    private User(Long id, String email, String passwordHash, Role role, Instant createdAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.createdAt = createdAt;
    }

    /** Tạo user mới (chưa có id). Email được chuẩn hóa về chữ thường. */
    public static User create(String email, String passwordHash, Role role, Instant now) {
        Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(now, "now must not be null");
        return new User(null, validEmail(email), passwordHash, role, now);
    }

    /** Dựng lại user từ dữ liệu đã lưu. */
    public static User reconstitute(Long id, String email, String passwordHash, Role role, Instant createdAt) {
        return new User(id, email, passwordHash, role, createdAt);
    }

    public User withRole(Role newRole) {
        Objects.requireNonNull(newRole, "role must not be null");
        return new User(id, email, passwordHash, newRole, createdAt);
    }

    /** Chỉ cắt khoảng trắng và đổi chữ thường, không kiểm tra hợp lệ (dùng khi đăng nhập). */
    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /** Chuẩn hóa và kiểm tra email, ném InvalidUserException nếu sai. */
    public static String validEmail(String email) {
        String normalized = normalizeEmail(email);
        if (normalized.isEmpty()) {
            throw new InvalidUserException("Email must not be blank");
        }
        if (normalized.length() > MAX_EMAIL_LENGTH || !EMAIL.matcher(normalized).matches()) {
            throw new InvalidUserException("Email is not valid");
        }
        return normalized;
    }

    /** Kiểm tra mật khẩu gốc trước khi băm. */
    public static void validateRawPassword(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new InvalidUserException(
                    "Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new InvalidUserException(
                    "Password must not exceed " + MAX_PASSWORD_BYTES + " bytes");
        }
    }

    public Long id() { return id; }
    public String email() { return email; }
    public String passwordHash() { return passwordHash; }
    public Role role() { return role; }
    public Instant createdAt() { return createdAt; }

    /** Cố ý không in passwordHash. */
    @Override
    public String toString() {
        return "User[id=" + id + ", email=" + email + ", role=" + role + "]";
    }
}
