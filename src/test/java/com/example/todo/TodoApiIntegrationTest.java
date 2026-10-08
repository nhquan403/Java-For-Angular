package com.example.todo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test tích hợp: khởi động cả ứng dụng thật (Spring + Security + Flyway + H2) trên cổng ngẫu nhiên
 * rồi gọi API bằng HTTP. Kiểm tra toàn bộ đường đi từ controller đến database, gồm đăng nhập và phân quyền.
 *
 * Profile mặc định là dev nên có sẵn khóa JWT và tài khoản admin@example.com / Admin#12345.
 * BCrypt hạ xuống mức thấp nhất để test chạy nhanh.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.security.bcrypt-strength=4")
class TodoApiIntegrationTest {

    private static final String PASSWORD = "password123";
    private static final Pattern ACCESS = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern REFRESH = Pattern.compile("\"refreshToken\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");

    @Value("${local.server.port}")
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    /** Mỗi test dùng email riêng vì các test chia sẻ chung một database. */
    private String aliceEmail;
    private String bobEmail;

    @BeforeEach
    void newEmails() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        aliceEmail = "alice-" + suffix + "@example.com";
        bobEmail = "bob-" + suffix + "@example.com";
    }

    // ------------------------------------------------------------------ helpers

    private HttpResponse<String> send(String method, String path, String json, String bearer) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        if (json != null) {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(json));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String credentials(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private static String grab(Pattern pattern, String body) {
        Matcher matcher = pattern.matcher(body);
        assertThat(matcher.find()).as("pattern %s in %s", pattern, body).isTrue();
        return matcher.group(1);
    }

    /** Hai token của một lần đăng nhập. */
    private record Session(String access, String refresh) {
    }

    private Session sessionFrom(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(200);
        return new Session(grab(ACCESS, response.body()), grab(REFRESH, response.body()));
    }

    private Session login(String email, String password) throws Exception {
        return sessionFrom(send("POST", "/api/auth/login", credentials(email, password), null));
    }

    /** Đăng ký rồi đăng nhập, trả về cặp token. */
    private Session registerAndLogin(String email) throws Exception {
        HttpResponse<String> registered = send("POST", "/api/auth/register", credentials(email, PASSWORD), null);
        assertThat(registered.statusCode()).isEqualTo(201);
        return login(email, PASSWORD);
    }

    private Session adminSession() throws Exception {
        return login("admin@example.com", "Admin#12345");
    }

    private String refreshBody(String refreshToken) {
        return "{\"refreshToken\":\"" + refreshToken + "\"}";
    }

    /** Tạo todo và trả về đường dẫn trong header Location, ví dụ /api/todos/5 */
    private String createTodo(Session session, String title) throws Exception {
        HttpResponse<String> response = send("POST", "/api/todos", "{\"title\":\"" + title + "\"}", session.access());
        assertThat(response.statusCode()).isEqualTo(201);
        String location = response.headers().firstValue("Location").orElseThrow();
        return URI.create(location).getPath();
    }

    // ------------------------------------------------------------------ xác thực

    @Test
    void todoEndpointsRequireLogin() throws Exception {
        HttpResponse<String> response = send("GET", "/api/todos", null, null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("problem+json");
        assertThat(response.body()).contains("401");
    }

    @Test
    void garbageAndForgedTokensAreRejected() throws Exception {
        assertThat(send("GET", "/api/todos", null, "not-a-jwt").statusCode()).isEqualTo(401);

        // Token giả không có chữ ký ("alg":"none") tự xưng là ADMIN
        String forged = "eyJhbGciOiJub25lIn0.eyJzdWIiOiIxIiwicm9sZXMiOlsiQURNSU4iXX0.";
        assertThat(send("GET", "/api/admin/users", null, forged).statusCode()).isEqualTo(401);
    }

    @Test
    void registerNeverReturnsThePassword() throws Exception {
        HttpResponse<String> response = send("POST", "/api/auth/register", credentials(aliceEmail, PASSWORD), null);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body()).contains(aliceEmail).contains("\"role\":\"USER\"")
                .doesNotContain(PASSWORD).doesNotContain("password");
    }

    @Test
    void registerRejectsDuplicateAndInvalidInput() throws Exception {
        send("POST", "/api/auth/register", credentials(aliceEmail, PASSWORD), null);

        assertThat(send("POST", "/api/auth/register", credentials(aliceEmail.toUpperCase(), PASSWORD), null)
                .statusCode()).isEqualTo(409);
        assertThat(send("POST", "/api/auth/register", credentials("not-an-email", PASSWORD), null)
                .statusCode()).isEqualTo(400);
        assertThat(send("POST", "/api/auth/register", credentials(bobEmail, "short"), null)
                .statusCode()).isEqualTo(400);
    }

    @Test
    void loginFailsWithTheSameErrorForWrongPasswordAndUnknownEmail() throws Exception {
        send("POST", "/api/auth/register", credentials(aliceEmail, PASSWORD), null);

        HttpResponse<String> wrongPassword = send("POST", "/api/auth/login", credentials(aliceEmail, "wrong-pass"), null);
        HttpResponse<String> unknownEmail = send("POST", "/api/auth/login", credentials(bobEmail, PASSWORD), null);

        assertThat(wrongPassword.statusCode()).isEqualTo(401);
        assertThat(unknownEmail.statusCode()).isEqualTo(401);
        assertThat(wrongPassword.body()).isEqualTo(unknownEmail.body());
    }

    @Test
    void meReturnsTheLoggedInUser() throws Exception {
        Session alice = registerAndLogin(aliceEmail);

        HttpResponse<String> me = send("GET", "/api/auth/me", null, alice.access());

        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.body()).contains(aliceEmail).contains("\"role\":\"USER\"");
    }

    // ------------------------------------------------------------------ refresh token

    @Test
    void refreshRotatesTokensAndReuseRevokesEverything() throws Exception {
        Session first = registerAndLogin(aliceEmail);

        HttpResponse<String> refreshed = send("POST", "/api/auth/refresh", refreshBody(first.refresh()), null);
        Session second = sessionFrom(refreshed);
        assertThat(second.refresh()).isNotEqualTo(first.refresh());
        assertThat(send("GET", "/api/todos", null, second.access()).statusCode()).isEqualTo(200);

        // Dùng lại token cũ: bị từ chối và thu hồi luôn token mới (nghi bị đánh cắp)
        assertThat(send("POST", "/api/auth/refresh", refreshBody(first.refresh()), null).statusCode())
                .isEqualTo(401);
        assertThat(send("POST", "/api/auth/refresh", refreshBody(second.refresh()), null).statusCode())
                .isEqualTo(401);
    }

    @Test
    void refreshRejectsUnknownToken() throws Exception {
        assertThat(send("POST", "/api/auth/refresh", refreshBody("made-up"), null).statusCode()).isEqualTo(401);
        assertThat(send("POST", "/api/auth/refresh", "{}", null).statusCode()).isEqualTo(400);
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        Session alice = registerAndLogin(aliceEmail);

        assertThat(send("POST", "/api/auth/logout", refreshBody(alice.refresh()), null).statusCode())
                .isEqualTo(204);
        assertThat(send("POST", "/api/auth/refresh", refreshBody(alice.refresh()), null).statusCode())
                .isEqualTo(401);
        // Đăng xuất với token lạ vẫn là 204, không tiết lộ token nào có thật
        assertThat(send("POST", "/api/auth/logout", refreshBody("made-up"), null).statusCode()).isEqualTo(204);
    }

    // ------------------------------------------------------------------ quyền sở hữu todo

    @Test
    void createThenGet() throws Exception {
        Session alice = registerAndLogin(aliceEmail);

        HttpResponse<String> created = send("POST", "/api/todos",
                "{\"title\":\"Learn Hexagonal\",\"description\":\"Ports and adapters\"}", alice.access());

        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.body()).contains("\"title\":\"Learn Hexagonal\"")
                .contains("\"completed\":false")
                .contains("\"version\":0");

        String path = URI.create(created.headers().firstValue("Location").orElseThrow()).getPath();
        HttpResponse<String> fetched = send("GET", path, null, alice.access());

        assertThat(fetched.statusCode()).isEqualTo(200);
        assertThat(fetched.body()).contains("Learn Hexagonal");
    }

    @Test
    void usersCannotSeeOrTouchEachOthersTodos() throws Exception {
        Session alice = registerAndLogin(aliceEmail);
        Session bob = registerAndLogin(bobEmail);
        String alicePath = createTodo(alice, "Alice secret");

        // 404 chứ không phải 403: Bob không biết id đó có tồn tại hay không
        assertThat(send("GET", alicePath, null, bob.access()).statusCode()).isEqualTo(404);
        assertThat(send("PATCH", alicePath + "/complete", null, bob.access()).statusCode()).isEqualTo(404);
        assertThat(send("PUT", alicePath, "{\"title\":\"Hacked\"}", bob.access()).statusCode()).isEqualTo(404);
        assertThat(send("DELETE", alicePath, null, bob.access()).statusCode()).isEqualTo(404);
        assertThat(send("GET", "/api/todos", null, bob.access()).body()).doesNotContain("Alice secret");

        // Todo của Alice vẫn nguyên vẹn
        HttpResponse<String> stillThere = send("GET", alicePath, null, alice.access());
        assertThat(stillThere.statusCode()).isEqualTo(200);
        assertThat(stillThere.body()).contains("Alice secret").contains("\"completed\":false");
    }

    @Test
    void adminSeesAndManagesEveryonesTodos() throws Exception {
        Session alice = registerAndLogin(aliceEmail);
        Session admin = adminSession();
        String alicePath = createTodo(alice, "Visible to admin");

        assertThat(send("GET", alicePath, null, admin.access()).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/todos?size=100", null, admin.access()).body()).contains("Visible to admin");
        assertThat(send("DELETE", alicePath, null, admin.access()).statusCode()).isEqualTo(204);
    }

    // ------------------------------------------------------------------ quyền ADMIN

    @Test
    void adminEndpointsAreForbiddenForRegularUsers() throws Exception {
        Session alice = registerAndLogin(aliceEmail);

        HttpResponse<String> response = send("GET", "/api/admin/users", null, alice.access());

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("problem+json");
        assertThat(send("GET", "/api/admin/users", null, null).statusCode()).isEqualTo(401);
    }

    @Test
    void adminCanListUsers() throws Exception {
        registerAndLogin(aliceEmail);
        Session admin = adminSession();

        HttpResponse<String> response = send("GET", "/api/admin/users?size=100", null, admin.access());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(aliceEmail).contains("admin@example.com").doesNotContain("password");
    }

    @Test
    void promotingAUserForcesRelogin() throws Exception {
        Session alice = registerAndLogin(aliceEmail);
        Session admin = adminSession();
        String aliceId = grab(ID, send("GET", "/api/auth/me", null, alice.access()).body());

        HttpResponse<String> promoted = send("PATCH", "/api/admin/users/" + aliceId + "/role",
                "{\"role\":\"ADMIN\"}", admin.access());
        assertThat(promoted.statusCode()).isEqualTo(200);
        assertThat(promoted.body()).contains("\"role\":\"ADMIN\"");

        // Refresh token cũ bị thu hồi, phải đăng nhập lại
        assertThat(send("POST", "/api/auth/refresh", refreshBody(alice.refresh()), null).statusCode())
                .isEqualTo(401);
        Session aliceAgain = login(aliceEmail, PASSWORD);
        assertThat(send("GET", "/api/admin/users", null, aliceAgain.access()).statusCode()).isEqualTo(200);
    }

    @Test
    void invalidRoleIs400AndAdminCannotChangeOwnRole() throws Exception {
        Session admin = adminSession();
        String adminId = grab(ID, send("GET", "/api/auth/me", null, admin.access()).body());

        assertThat(send("PATCH", "/api/admin/users/" + adminId + "/role",
                "{\"role\":\"SUPERUSER\"}", admin.access()).statusCode()).isEqualTo(400);
        assertThat(send("PATCH", "/api/admin/users/" + adminId + "/role",
                "{\"role\":\"USER\"}", admin.access()).statusCode()).isEqualTo(400);
        assertThat(send("PATCH", "/api/admin/users/999999/role",
                "{\"role\":\"USER\"}", admin.access()).statusCode()).isEqualTo(404);
    }

    // ------------------------------------------------------------------ hành vi chung của API todo

    @Test
    void blankTitleReturns400WithFieldErrors() throws Exception {
        Session alice = registerAndLogin(aliceEmail);

        HttpResponse<String> response = send("POST", "/api/todos", "{\"title\":\"   \"}", alice.access());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("problem+json");
        assertThat(response.body()).contains("errors").contains("title");
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        Session alice = registerAndLogin(aliceEmail);

        assertThat(send("POST", "/api/todos", "{not json", alice.access()).statusCode()).isEqualTo(400);
    }

    @Test
    void missingTodoReturns404() throws Exception {
        Session alice = registerAndLogin(aliceEmail);

        assertThat(send("GET", "/api/todos/999999", null, alice.access()).statusCode()).isEqualTo(404);
    }

    @Test
    void nonNumericIdReturns400() throws Exception {
        Session alice = registerAndLogin(aliceEmail);

        assertThat(send("GET", "/api/todos/abc", null, alice.access()).statusCode()).isEqualTo(400);
    }

    @Test
    void unknownRouteIs404NotServerError() throws Exception {
        Session alice = registerAndLogin(aliceEmail);

        assertThat(send("GET", "/api/nothing-here", null, alice.access()).statusCode()).isEqualTo(404);
    }

    @Test
    void wrongMethodIs405NotServerError() throws Exception {
        Session alice = registerAndLogin(aliceEmail);

        assertThat(send("DELETE", "/api/todos", null, alice.access()).statusCode()).isEqualTo(405);
    }

    @Test
    void updateWithStaleVersionReturns409() throws Exception {
        Session alice = registerAndLogin(aliceEmail);
        String path = createTodo(alice, "Concurrent edit");

        HttpResponse<String> first = send("PUT", path, "{\"title\":\"First\",\"version\":0}", alice.access());
        HttpResponse<String> second = send("PUT", path, "{\"title\":\"Second\",\"version\":0}", alice.access());

        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(first.body()).contains("\"version\":1");
        assertThat(second.statusCode()).isEqualTo(409);
        assertThat(send("GET", path, null, alice.access()).body()).contains("First");
    }

    @Test
    void completeAndReopen() throws Exception {
        Session alice = registerAndLogin(aliceEmail);
        String path = createTodo(alice, "Finish me");

        assertThat(send("PATCH", path + "/complete", null, alice.access()).body()).contains("\"completed\":true");
        assertThat(send("PATCH", path + "/reopen", null, alice.access()).body()).contains("\"completed\":false");
    }

    @Test
    void listIsPaginatedAndValidated() throws Exception {
        Session alice = registerAndLogin(aliceEmail);
        createTodo(alice, "Page 1");
        createTodo(alice, "Page 2");
        createTodo(alice, "Page 3");

        HttpResponse<String> page = send("GET", "/api/todos?page=0&size=2&sortBy=id&direction=asc", null, alice.access());
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("\"size\":2").contains("\"last\":false");

        assertThat(send("GET", "/api/todos?sortBy=password", null, alice.access()).statusCode()).isEqualTo(400);
        assertThat(send("GET", "/api/todos?size=1000", null, alice.access()).statusCode()).isEqualTo(400);
        assertThat(send("GET", "/api/todos?page=-1", null, alice.access()).statusCode()).isEqualTo(400);
    }

    @Test
    void deleteThenGetReturns404() throws Exception {
        Session alice = registerAndLogin(aliceEmail);
        String path = createTodo(alice, "Delete me");

        assertThat(send("DELETE", path, null, alice.access()).statusCode()).isEqualTo(204);
        assertThat(send("GET", path, null, alice.access()).statusCode()).isEqualTo(404);
    }

    // ------------------------------------------------------------------ hạ tầng

    @Test
    void requestIdIsEchoedAndGeneratedWhenMissing() throws Exception {
        HttpRequest echoRequest = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health"))
                .header("X-Request-Id", "my-trace-123").GET().build();
        HttpResponse<String> echoed = http.send(echoRequest, HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> generated = send("GET", "/actuator/health", null, null);

        assertThat(echoed.headers().firstValue("X-Request-Id")).contains("my-trace-123");
        assertThat(generated.headers().firstValue("X-Request-Id")).isPresent();
    }

    @Test
    void errorResponsesAlsoCarryTheRequestId() throws Exception {
        // 401 do Spring Security trả, vẫn phải có request id nhờ CorrelationIdFilter chạy trước
        assertThat(send("GET", "/api/todos", null, null).headers().firstValue("X-Request-Id")).isPresent();
    }

    @Test
    void healthEndpointIsPublicAndUp() throws Exception {
        HttpResponse<String> response = send("GET", "/actuator/health", null, null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("UP");
    }
}
