package com.example.todo;

import com.example.todo.ApiTestClient.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Pattern;

import static com.example.todo.ApiTestClient.PASSWORD;
import static com.example.todo.ApiTestClient.credentials;
import static com.example.todo.ApiTestClient.grab;
import static com.example.todo.ApiTestClient.sessionFrom;
import static com.example.todo.ApiTestClient.uniqueEmail;
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

    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");

    @Value("${local.server.port}")
    private int port;

    private ApiTestClient api;

    /** Mỗi test dùng email riêng vì các test chia sẻ chung một database. */
    private String aliceEmail;
    private String bobEmail;

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(port);
        aliceEmail = uniqueEmail("alice");
        bobEmail = uniqueEmail("bob");
    }

    // ------------------------------------------------------------------ helpers

    private static String refreshBody(String refreshToken) {
        return "{\"refreshToken\":\"" + refreshToken + "\"}";
    }

    /** Tạo todo và trả về đường dẫn trong header Location, ví dụ /api/todos/5 */
    private String createTodo(Session session, String title) throws Exception {
        HttpResponse<String> response = api.send("POST", "/api/todos", "{\"title\":\"" + title + "\"}", session.access());
        assertThat(response.statusCode()).isEqualTo(201);
        String location = response.headers().firstValue("Location").orElseThrow();
        return URI.create(location).getPath();
    }

    // ------------------------------------------------------------------ xác thực

    @Test
    void todoEndpointsRequireLogin() throws Exception {
        HttpResponse<String> response = api.send("GET", "/api/todos", null, null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("problem+json");
        assertThat(response.body()).contains("401");
    }

    @Test
    void garbageAndForgedTokensAreRejected() throws Exception {
        assertThat(api.send("GET", "/api/todos", null, "not-a-jwt").statusCode()).isEqualTo(401);

        // Token giả không có chữ ký ("alg":"none") tự xưng là ADMIN
        String forged = "eyJhbGciOiJub25lIn0.eyJzdWIiOiIxIiwicm9sZXMiOlsiQURNSU4iXX0.";
        assertThat(api.send("GET", "/api/admin/users", null, forged).statusCode()).isEqualTo(401);
    }

    @Test
    void registerNeverReturnsThePassword() throws Exception {
        HttpResponse<String> response = api.send("POST", "/api/auth/register", credentials(aliceEmail, PASSWORD), null);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body()).contains(aliceEmail).contains("\"role\":\"USER\"")
                .doesNotContain(PASSWORD).doesNotContain("password");
    }

    @Test
    void registerRejectsDuplicateAndInvalidInput() throws Exception {
        api.send("POST", "/api/auth/register", credentials(aliceEmail, PASSWORD), null);

        assertThat(api.send("POST", "/api/auth/register", credentials(aliceEmail.toUpperCase(), PASSWORD), null)
                .statusCode()).isEqualTo(409);
        assertThat(api.send("POST", "/api/auth/register", credentials("not-an-email", PASSWORD), null)
                .statusCode()).isEqualTo(400);
        assertThat(api.send("POST", "/api/auth/register", credentials(bobEmail, "short"), null)
                .statusCode()).isEqualTo(400);
    }

    @Test
    void loginFailsWithTheSameErrorForWrongPasswordAndUnknownEmail() throws Exception {
        api.send("POST", "/api/auth/register", credentials(aliceEmail, PASSWORD), null);

        HttpResponse<String> wrongPassword = api.send("POST", "/api/auth/login", credentials(aliceEmail, "wrong-pass"), null);
        HttpResponse<String> unknownEmail = api.send("POST", "/api/auth/login", credentials(bobEmail, PASSWORD), null);

        assertThat(wrongPassword.statusCode()).isEqualTo(401);
        assertThat(unknownEmail.statusCode()).isEqualTo(401);
        assertThat(wrongPassword.body()).isEqualTo(unknownEmail.body());
    }

    @Test
    void meReturnsTheLoggedInUser() throws Exception {
        Session alice = api.registerAndLogin(aliceEmail);

        HttpResponse<String> me = api.send("GET", "/api/auth/me", null, alice.access());

        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.body()).contains(aliceEmail).contains("\"role\":\"USER\"");
    }

    // ------------------------------------------------------------------ refresh token

    @Test
    void refreshRotatesTokensAndReuseRevokesEverything() throws Exception {
        Session first = api.registerAndLogin(aliceEmail);

        HttpResponse<String> refreshed = api.send("POST", "/api/auth/refresh", refreshBody(first.refresh()), null);
        Session second = sessionFrom(refreshed);
        assertThat(second.refresh()).isNotEqualTo(first.refresh());
        assertThat(api.send("GET", "/api/todos", null, second.access()).statusCode()).isEqualTo(200);

        // Dùng lại token cũ: bị từ chối và thu hồi luôn token mới (nghi bị đánh cắp)
        assertThat(api.send("POST", "/api/auth/refresh", refreshBody(first.refresh()), null).statusCode())
                .isEqualTo(401);
        assertThat(api.send("POST", "/api/auth/refresh", refreshBody(second.refresh()), null).statusCode())
                .isEqualTo(401);
    }

    @Test
    void refreshRejectsUnknownToken() throws Exception {
        assertThat(api.send("POST", "/api/auth/refresh", refreshBody("made-up"), null).statusCode()).isEqualTo(401);
        assertThat(api.send("POST", "/api/auth/refresh", "{}", null).statusCode()).isEqualTo(400);
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        Session alice = api.registerAndLogin(aliceEmail);

        assertThat(api.send("POST", "/api/auth/logout", refreshBody(alice.refresh()), null).statusCode())
                .isEqualTo(204);
        assertThat(api.send("POST", "/api/auth/refresh", refreshBody(alice.refresh()), null).statusCode())
                .isEqualTo(401);
        // Đăng xuất với token lạ vẫn là 204, không tiết lộ token nào có thật
        assertThat(api.send("POST", "/api/auth/logout", refreshBody("made-up"), null).statusCode()).isEqualTo(204);
    }

    // ------------------------------------------------------------------ quyền sở hữu todo

    @Test
    void createThenGet() throws Exception {
        Session alice = api.registerAndLogin(aliceEmail);

        HttpResponse<String> created = api.send("POST", "/api/todos",
                "{\"title\":\"Learn Hexagonal\",\"description\":\"Ports and adapters\"}", alice.access());

        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.body()).contains("\"title\":\"Learn Hexagonal\"")
                .contains("\"completed\":false")
                .contains("\"version\":0");

        String path = URI.create(created.headers().firstValue("Location").orElseThrow()).getPath();
        HttpResponse<String> fetched = api.send("GET", path, null, alice.access());

        assertThat(fetched.statusCode()).isEqualTo(200);
        assertThat(fetched.body()).contains("Learn Hexagonal");
    }

    @Test
    void usersCannotSeeOrTouchEachOthersTodos() throws Exception {
        Session alice = api.registerAndLogin(aliceEmail);
        Session bob = api.registerAndLogin(bobEmail);
        String alicePath = createTodo(alice, "Alice secret");

        // 404 chứ không phải 403: Bob không biết id đó có tồn tại hay không
        assertThat(api.send("GET", alicePath, null, bob.access()).statusCode()).isEqualTo(404);
        assertThat(api.send("PATCH", alicePath + "/complete", null, bob.access()).statusCode()).isEqualTo(404);
        assertThat(api.send("PUT", alicePath, "{\"title\":\"Hacked\"}", bob.access()).statusCode()).isEqualTo(404);
        assertThat(api.send("DELETE", alicePath, null, bob.access()).statusCode()).isEqualTo(404);
        assertThat(api.send("GET", "/api/todos", null, bob.access()).body()).doesNotContain("Alice secret");

        // Todo của Alice vẫn nguyên vẹn
        HttpResponse<String> stillThere = api.send("GET", alicePath, null, alice.access());
        assertThat(stillThere.statusCode()).isEqualTo(200);
        assertThat(stillThere.body()).contains("Alice secret").contains("\"completed\":false");
    }

    @Test
    void adminSeesAndManagesEveryonesTodos() throws Exception {
        Session alice = api.registerAndLogin(aliceEmail);
        Session admin = api.adminLogin();
        String alicePath = createTodo(alice, "Visible to admin");

        assertThat(api.send("GET", alicePath, null, admin.access()).statusCode()).isEqualTo(200);
        assertThat(api.send("GET", "/api/todos?size=100", null, admin.access()).body()).contains("Visible to admin");
        assertThat(api.send("DELETE", alicePath, null, admin.access()).statusCode()).isEqualTo(204);
    }

    // ------------------------------------------------------------------ quyền ADMIN

    @Test
    void adminEndpointsAreForbiddenForRegularUsers() throws Exception {
        Session alice = api.registerAndLogin(aliceEmail);

        HttpResponse<String> response = api.send("GET", "/api/admin/users", null, alice.access());

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("problem+json");
        assertThat(api.send("GET", "/api/admin/users", null, null).statusCode()).isEqualTo(401);
    }

    @Test
    void adminCanListUsers() throws Exception {
        api.registerAndLogin(aliceEmail);
        Session admin = api.adminLogin();

        HttpResponse<String> response = api.send("GET", "/api/admin/users?size=100", null, admin.access());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(aliceEmail).contains("admin@example.com").doesNotContain("password");
    }

    @Test
    void promotingAUserForcesRelogin() throws Exception {
        Session alice = api.registerAndLogin(aliceEmail);
        Session admin = api.adminLogin();
        String aliceId = grab(ID, api.send("GET", "/api/auth/me", null, alice.access()).body());

        HttpResponse<String> promoted = api.send("PATCH", "/api/admin/users/" + aliceId + "/role",
                "{\"role\":\"ADMIN\"}", admin.access());
        assertThat(promoted.statusCode()).isEqualTo(200);
        assertThat(promoted.body()).contains("\"role\":\"ADMIN\"");

        // Refresh token cũ bị thu hồi, phải đăng nhập lại
        assertThat(api.send("POST", "/api/auth/refresh", refreshBody(alice.refresh()), null).statusCode())
                .isEqualTo(401);
        Session aliceAgain = api.login(aliceEmail, PASSWORD);
        assertThat(api.send("GET", "/api/admin/users", null, aliceAgain.access()).statusCode()).isEqualTo(200);
    }

    @Test
    void invalidRoleIs400AndAdminCannotChangeOwnRole() throws Exception {
        Session admin = api.adminLogin();
        String adminId = grab(ID, api.send("GET", "/api/auth/me", null, admin.access()).body());

        assertThat(api.send("PATCH", "/api/admin/users/" + adminId + "/role",
                "{\"role\":\"SUPERUSER\"}", admin.access()).statusCode()).isEqualTo(400);
        assertThat(api.send("PATCH", "/api/admin/users/" + adminId + "/role",
                "{\"role\":\"USER\"}", admin.access()).statusCode()).isEqualTo(400);
        assertThat(api.send("PATCH", "/api/admin/users/999999/role",
                "{\"role\":\"USER\"}", admin.access()).statusCode()).isEqualTo(404);
    }

    // ------------------------------------------------------------------ hành vi chung của API todo

    @Test
    void blankTitleReturns400WithFieldErrors() throws Exception {
        Session alice = api.registerAndLogin(aliceEmail);

        HttpResponse<String> response = api.send("POST", "/api/todos", "{\"title\":\"   \"}", alice.access());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("problem+json");
        assertThat(response.body()).contains("errors").contains("title");
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        Session alice = api.registerAndLogin(aliceEmail);

        assertThat(api.send("POST", "/api/todos", "{not json", alice.access()).statusCode()).isEqualTo(400);
    }

    @Test
    void missingTodoReturns404() throws Exception {
        Session alice = api.registerAndLogin(aliceEmail);

        assertThat(api.send("GET", "/api/todos/999999", null, alice.access()).statusCode()).isEqualTo(404);
    }

    @Test
    void nonNumericIdReturns400() throws Exception {
        Session alice = api.registerAndLogin(aliceEmail);

        assertThat(api.send("GET", "/api/todos/abc", null, alice.access()).statusCode()).isEqualTo(400);
    }

    @Test
    void unknownRouteIs404NotServerError() throws Exception {
        Session alice = api.registerAndLogin(aliceEmail);

        assertThat(api.send("GET", "/api/nothing-here", null, alice.access()).statusCode()).isEqualTo(404);
    }

    @Test
    void wrongMethodIs405NotServerError() throws Exception {
        Session alice = api.registerAndLogin(aliceEmail);

        assertThat(api.send("DELETE", "/api/todos", null, alice.access()).statusCode()).isEqualTo(405);
    }

    @Test
    void updateWithStaleVersionReturns409() throws Exception {
        Session alice = api.registerAndLogin(aliceEmail);
        String path = createTodo(alice, "Concurrent edit");

        HttpResponse<String> first = api.send("PUT", path, "{\"title\":\"First\",\"version\":0}", alice.access());
        HttpResponse<String> second = api.send("PUT", path, "{\"title\":\"Second\",\"version\":0}", alice.access());

        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(first.body()).contains("\"version\":1");
        assertThat(second.statusCode()).isEqualTo(409);
        assertThat(api.send("GET", path, null, alice.access()).body()).contains("First");
    }

    @Test
    void completeAndReopen() throws Exception {
        Session alice = api.registerAndLogin(aliceEmail);
        String path = createTodo(alice, "Finish me");

        assertThat(api.send("PATCH", path + "/complete", null, alice.access()).body()).contains("\"completed\":true");
        assertThat(api.send("PATCH", path + "/reopen", null, alice.access()).body()).contains("\"completed\":false");
    }

    @Test
    void listIsPaginatedAndValidated() throws Exception {
        Session alice = api.registerAndLogin(aliceEmail);
        createTodo(alice, "Page 1");
        createTodo(alice, "Page 2");
        createTodo(alice, "Page 3");

        HttpResponse<String> page = api.send("GET", "/api/todos?page=0&size=2&sortBy=id&direction=asc", null, alice.access());
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("\"size\":2").contains("\"last\":false");

        assertThat(api.send("GET", "/api/todos?sortBy=password", null, alice.access()).statusCode()).isEqualTo(400);
        assertThat(api.send("GET", "/api/todos?size=1000", null, alice.access()).statusCode()).isEqualTo(400);
        assertThat(api.send("GET", "/api/todos?page=-1", null, alice.access()).statusCode()).isEqualTo(400);
    }

    @Test
    void deleteThenGetReturns404() throws Exception {
        Session alice = api.registerAndLogin(aliceEmail);
        String path = createTodo(alice, "Delete me");

        assertThat(api.send("DELETE", path, null, alice.access()).statusCode()).isEqualTo(204);
        assertThat(api.send("GET", path, null, alice.access()).statusCode()).isEqualTo(404);
    }

    // ------------------------------------------------------------------ hạ tầng

    @Test
    void requestIdIsEchoedAndGeneratedWhenMissing() throws Exception {
        HttpRequest echoRequest = HttpRequest.newBuilder(api.uri("/actuator/health"))
                .header("X-Request-Id", "my-trace-123").GET().build();
        HttpResponse<String> echoed = api.http().send(echoRequest, HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> generated = api.send("GET", "/actuator/health", null, null);

        assertThat(echoed.headers().firstValue("X-Request-Id")).contains("my-trace-123");
        assertThat(generated.headers().firstValue("X-Request-Id")).isPresent();
    }

    @Test
    void errorResponsesAlsoCarryTheRequestId() throws Exception {
        // 401 do Spring Security trả, vẫn phải có request id nhờ CorrelationIdFilter chạy trước
        assertThat(api.send("GET", "/api/todos", null, null).headers().firstValue("X-Request-Id")).isPresent();
    }

    @Test
    void healthEndpointIsPublicAndUp() throws Exception {
        HttpResponse<String> response = api.send("GET", "/actuator/health", null, null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("UP");
    }
}
