package com.example.todo.adapter.in.web;

import com.example.todo.adapter.in.web.dto.LoginRequest;
import com.example.todo.adapter.in.web.dto.RefreshRequest;
import com.example.todo.adapter.in.web.dto.RegisterRequest;
import com.example.todo.adapter.in.web.dto.TokenResponse;
import com.example.todo.adapter.in.web.dto.UserResponse;
import com.example.todo.application.port.in.GetUserUseCase;
import com.example.todo.application.port.in.LoginUseCase;
import com.example.todo.application.port.in.LogoutUseCase;
import com.example.todo.application.port.in.RefreshTokenUseCase;
import com.example.todo.application.port.in.RegisterUserUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * INBOUND ADAPTER: đăng ký, đăng nhập, làm mới token, đăng xuất, xem thông tin của mình.
 * Bốn endpoint đầu mở công khai (xem SecurityConfig), /me cần access token.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RegisterUserUseCase registerUser;
    private final LoginUseCase login;
    private final RefreshTokenUseCase refreshToken;
    private final LogoutUseCase logout;
    private final GetUserUseCase getUser;

    public AuthController(RegisterUserUseCase registerUser,
                          LoginUseCase login,
                          RefreshTokenUseCase refreshToken,
                          LogoutUseCase logout,
                          GetUserUseCase getUser) {
        this.registerUser = registerUser;
        this.login = login;
        this.refreshToken = refreshToken;
        this.logout = logout;
        this.getUser = getUser;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        var user = registerUser.register(
                new RegisterUserUseCase.Command(request.email(), request.password()));
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return TokenResponse.from(login.login(
                new LoginUseCase.Command(request.email(), request.password())));
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return TokenResponse.from(refreshToken.refresh(request.refreshToken()));
    }

    /** Luôn trả 204, kể cả khi token không tồn tại, để không tiết lộ token nào hợp lệ. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        logout.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return UserResponse.from(getUser.getById(AuthenticatedActor.from(authentication).userId()));
    }
}
