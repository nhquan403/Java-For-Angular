package com.example.todo.adapter.out.security;

import com.example.todo.application.port.out.PasswordHasherPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * OUTBOUND ADAPTER: băm mật khẩu bằng BCrypt (chậm có chủ đích để chống dò mật khẩu hàng loạt).
 * Mỗi lần băm có "muối" ngẫu nhiên riêng nên cùng một mật khẩu cho ra mã băm khác nhau.
 *
 * strength: độ chậm, mỗi +1 là chậm gấp đôi. Mặc định 10. Test dùng 4 (tối thiểu) cho nhanh.
 */
@Component
class BcryptPasswordHasher implements PasswordHasherPort {

    private final BCryptPasswordEncoder encoder;

    BcryptPasswordHasher(@Value("${app.security.bcrypt-strength:10}") int strength) {
        this.encoder = new BCryptPasswordEncoder(strength);
    }

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String hash) {
        return encoder.matches(rawPassword, hash);
    }
}
