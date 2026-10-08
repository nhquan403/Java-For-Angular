package com.example.todo.config;

import com.example.todo.adapter.in.realtime.RealtimeTicketStore;
import com.example.todo.adapter.in.realtime.TicketAuthenticationFilter;
import com.example.todo.adapter.in.web.ProblemJsonSecurityHandlers;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Quy định đường dẫn nào cần đăng nhập, đường dẫn nào cần role ADMIN.
 * Lưu ý: quy tắc "chỉ truy cập todo của mình" nằm trong TodoService (lõi), không nằm ở đây.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   ProblemJsonSecurityHandlers problemHandlers,
                                                   RealtimeTicketStore ticketStore) throws Exception {
        http
                // API chỉ dùng token trong header Authorization, không dùng cookie phiên
                // nên không có nguy cơ CSRF và không cần bật bảo vệ này.
                .csrf(csrf -> csrf.disable())
                // Dùng lại cấu hình CORS của Spring MVC (xem WebConfig).
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // SSE chạy bất đồng bộ: Spring "gửi lại" request một lần nữa (ASYNC dispatch) khi kết thúc luồng.
                // Request gốc đã được kiểm tra quyền rồi, client không thể tự tạo lượt gửi lại này, nên cho qua.
                // Thiếu dòng này, lúc luồng SSE kết thúc sẽ có lỗi 401 vô nghĩa trong log.
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/api/auth/logout").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                // Trình duyệt không gắn được header vào EventSource và WebSocket, nên hai đường này
                // còn nhận ticket dùng một lần (xem RealtimeTicketStore). Phải chạy trước bộ lọc Bearer.
                .addFilterBefore(new TicketAuthenticationFilter(ticketStore), BearerTokenAuthenticationFilter.class)
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(problemHandlers)
                        .accessDeniedHandler(problemHandlers))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(problemHandlers)
                        .accessDeniedHandler(problemHandlers));
        return http.build();
    }

    /**
     * Đọc claim "roles" trong JWT (ví dụ ["ADMIN"]) và đổi thành quyền ROLE_ADMIN,
     * để hasRole("ADMIN") ở trên hiểu được.
     */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
