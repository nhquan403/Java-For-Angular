package com.example.todo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HTTP client dùng chung cho các test tích hợp: gọi app đang chạy trên cổng ngẫu nhiên,
 * đăng ký và đăng nhập nhanh. Profile dev có sẵn tài khoản admin@example.com / Admin#12345.
 */
final class ApiTestClient {

    static final String PASSWORD = "password123";

    private static final Pattern ACCESS = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern REFRESH = Pattern.compile("\"refreshToken\"\\s*:\\s*\"([^\"]+)\"");

    /** Hai token của một lần đăng nhập. */
    record Session(String access, String refresh) {
    }

    private final HttpClient http = HttpClient.newHttpClient();
    private final int port;

    ApiTestClient(int port) {
        this.port = port;
    }

    HttpClient http() {
        return http;
    }

    URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    HttpResponse<String> send(String method, String path, String json, String bearer) throws Exception {
        return send(method, path, json, bearer, null);
    }

    /** @param json null thì gửi không có body; bearer, accept null thì không gửi header tương ứng */
    HttpResponse<String> send(String method, String path, String json, String bearer, String accept)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path));
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        if (accept != null) {
            builder.header("Accept", accept);
        }
        if (json != null) {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(json));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    static String credentials(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    /** Email chưa ai dùng, vì các test chia sẻ chung một database. */
    static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    static String grab(Pattern pattern, String body) {
        Matcher matcher = pattern.matcher(body);
        assertThat(matcher.find()).as("pattern %s in %s", pattern, body).isTrue();
        return matcher.group(1);
    }

    static Session sessionFrom(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(200);
        return new Session(grab(ACCESS, response.body()), grab(REFRESH, response.body()));
    }

    Session login(String email, String password) throws Exception {
        return sessionFrom(send("POST", "/api/auth/login", credentials(email, password), null));
    }

    /** Đăng ký (mật khẩu PASSWORD) rồi đăng nhập. */
    Session registerAndLogin(String email) throws Exception {
        HttpResponse<String> registered = send("POST", "/api/auth/register", credentials(email, PASSWORD), null);
        assertThat(registered.statusCode()).isEqualTo(201);
        return login(email, PASSWORD);
    }

    Session adminLogin() throws Exception {
        return login("admin@example.com", "Admin#12345");
    }
}
