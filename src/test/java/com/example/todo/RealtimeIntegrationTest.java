package com.example.todo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.example.todo.ApiTestClient.grab;
import static com.example.todo.ApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test tích hợp phần thời gian thực: SSE và WebSocket, qua mạng thật bằng HTTP client có sẵn của JDK.
 * Chạy ở chế độ mặc định (tắt Kafka): sự kiện được giao thẳng trong bộ nhớ. Đường đi qua Kafka và outbox
 * cần broker và PostgreSQL thật nên không nằm trong test này.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.security.bcrypt-strength=4")
class RealtimeIntegrationTest {

    private static final Pattern TICKET = Pattern.compile("\"ticket\"\\s*:\\s*\"([^\"]+)\"");

    @Value("${local.server.port}")
    private int port;

    private ApiTestClient api;
    private final List<AutoCloseable> toClose = new ArrayList<>();

    private String aliceEmail;
    private String bobEmail;

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(port);
        aliceEmail = uniqueEmail("alice");
        bobEmail = uniqueEmail("bob");
    }

    // ------------------------------------------------------------------ helpers

    private String newTicket(String accessToken) throws Exception {
        HttpResponse<String> response = api.send("POST", "/api/realtime/ticket", null, accessToken);
        assertThat(response.statusCode()).isEqualTo(200);
        return grab(TICKET, response.body());
    }

    private void createTodo(String accessToken, String title) throws Exception {
        assertThat(api.send("POST", "/api/todos", "{\"title\":\"" + title + "\"}", accessToken).statusCode())
                .isEqualTo(201);
    }

    /** Mở SSE (dùng ticket) và trả về hàng đợi các dòng nhận được. Trả về sau khi server đã trả header. */
    private BlockingQueue<String> openSse(String ticket) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        api.uri("/api/realtime/stream?ticket=" + ticket))
                .header("Accept", "text/event-stream").GET().build();
        HttpResponse<Stream<String>> response = api.http().sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                .get(5, TimeUnit.SECONDS);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("text/event-stream");

        BlockingQueue<String> lines = new LinkedBlockingQueue<>();
        toClose.add(response.body()::close);
        Thread.ofVirtual().start(() -> {
            try {
                response.body().forEach(lines::add);
            } catch (RuntimeException ignored) {
                // kết nối bị đóng khi test kết thúc
            }
        });
        return lines;
    }

    /** Chờ tới khi có một dòng "data:" chứa đoạn văn bản mong đợi. */
    private static String awaitData(BlockingQueue<String> lines, String contains) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            String line = lines.poll(100, TimeUnit.MILLISECONDS);
            if (line != null && line.startsWith("data:") && line.contains(contains)) {
                return line;
            }
        }
        throw new AssertionError("No SSE data containing '" + contains + "' within 5s");
    }

    private static boolean receivesNothingContaining(BlockingQueue<String> lines, String text) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(700);
        while (System.nanoTime() < deadline) {
            String line = lines.poll(50, TimeUnit.MILLISECONDS);
            if (line != null && line.startsWith("data:") && line.contains(text)) {
                return false;
            }
        }
        return true;
    }

    /** Client WebSocket gom các tin nhận được vào hàng đợi. */
    private static final class Collector implements WebSocket.Listener {
        final BlockingQueue<String> messages = new LinkedBlockingQueue<>();

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(Long.MAX_VALUE);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            messages.add(data.toString());
            return CompletableFuture.completedFuture(null);
        }
    }

    private WebSocket openWebSocket(String ticket, Collector collector) {
        WebSocket ws = api.http().newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + port + "/ws/todos?ticket=" + ticket), collector)
                .orTimeout(5, TimeUnit.SECONDS)
                .join();
        toClose.add(() -> ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye"));
        return ws;
    }

    @AfterEach
    void closeConnections() {
        for (AutoCloseable c : toClose) {
            try {
                c.close();
            } catch (Exception ignored) {
                // dọn dẹp, lỗi không quan trọng
            }
        }
        toClose.clear();
    }

    // ------------------------------------------------------------------ xác thực

    @Test
    void ticketEndpointAndStreamRequireLogin() throws Exception {
        assertThat(api.send("POST", "/api/realtime/ticket", null, null).statusCode()).isEqualTo(401);
        assertThat(api.send("GET", "/api/realtime/stream", null, null).statusCode()).isEqualTo(401);
        assertThat(api.send("GET", "/api/realtime/stream?ticket=made-up", null, null).statusCode()).isEqualTo(401);
    }

    @Test
    void ticketWorksOnlyOnce() throws Exception {
        String ticket = newTicket(api.registerAndLogin(aliceEmail).access());

        openSse(ticket); // lần đầu thành công (openSse tự kiểm tra 200)
        HttpResponse<String> second = api.send("GET", "/api/realtime/stream?ticket=" + ticket, null, null);

        assertThat(second.statusCode()).isEqualTo(401);
    }

    @Test
    void streamAlsoAcceptsABearerTokenForClientsThatCanSetHeaders() throws Exception {
        String token = api.registerAndLogin(aliceEmail).access();
        HttpRequest request = HttpRequest.newBuilder(api.uri("/api/realtime/stream"))
                .header("Authorization", "Bearer " + token).header("Accept", "text/event-stream").GET().build();

        HttpResponse<Stream<String>> response = api.http().sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                .get(5, TimeUnit.SECONDS);
        toClose.add(response.body()::close);

        assertThat(response.statusCode()).isEqualTo(200);
    }

    // ------------------------------------------------------------------ SSE

    @Test
    void sseDeliversTodoEventsToTheOwner() throws Exception {
        String alice = api.registerAndLogin(aliceEmail).access();
        BlockingQueue<String> lines = openSse(newTicket(alice));

        createTodo(alice, "Realtime hello");

        String data = awaitData(lines, "Realtime hello");
        assertThat(data).contains("\"type\":\"CREATED\"").contains("\"completed\":false");
    }

    @Test
    void sseOnlyDeliversYourOwnEvents() throws Exception {
        String alice = api.registerAndLogin(aliceEmail).access();
        String bob = api.registerAndLogin(bobEmail).access();
        BlockingQueue<String> aliceLines = openSse(newTicket(alice));
        BlockingQueue<String> bobLines = openSse(newTicket(bob));

        createTodo(alice, "Alice only");
        createTodo(bob, "Bob only");

        awaitData(aliceLines, "Alice only");
        awaitData(bobLines, "Bob only");
        assertThat(receivesNothingContaining(aliceLines, "Bob only")).isTrue();
        assertThat(receivesNothingContaining(bobLines, "Alice only")).isTrue();
    }

    @Test
    void adminReceivesEveryonesEvents() throws Exception {
        String alice = api.registerAndLogin(aliceEmail).access();
        BlockingQueue<String> adminLines = openSse(newTicket(api.adminLogin().access()));

        createTodo(alice, "Seen by admin");

        awaitData(adminLines, "Seen by admin");
    }

    @Test
    void updateCompleteAndDeleteAllProduceEvents() throws Exception {
        String alice = api.registerAndLogin(aliceEmail).access();
        BlockingQueue<String> lines = openSse(newTicket(alice));
        createTodo(alice, "Lifecycle");
        String created = awaitData(lines, "Lifecycle");
        String id = grab(Pattern.compile("\"todoId\":(\\d+)"), created);

        api.send("PATCH", "/api/todos/" + id + "/complete", null, alice);
        awaitData(lines, "\"type\":\"COMPLETED\"");
        api.send("DELETE", "/api/todos/" + id, null, alice);
        awaitData(lines, "\"type\":\"DELETED\"");
    }

    @Test
    void tooManyConnectionsGet429() throws Exception {
        String alice = api.registerAndLogin(aliceEmail).access();
        for (int i = 0; i < 5; i++) {
            openSse(newTicket(alice));
        }

        HttpResponse<String> sixth = api.send("GET", "/api/realtime/stream?ticket=" + newTicket(alice), null, null);

        assertThat(sixth.statusCode()).isEqualTo(429);
    }

    // ------------------------------------------------------------------ WebSocket

    @Test
    void webSocketDeliversTodoEvents() throws Exception {
        String alice = api.registerAndLogin(aliceEmail).access();
        Collector collector = new Collector();
        openWebSocket(newTicket(alice), collector);

        createTodo(alice, "Over the socket");

        String message = collector.messages.poll(5, TimeUnit.SECONDS);
        assertThat(message).contains("Over the socket").contains("\"type\":\"CREATED\"");
    }

    @Test
    void webSocketWithoutValidTicketIsRejected() {
        Collector collector = new Collector();
        CompletableFuture<WebSocket> attempt = api.http().newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + port + "/ws/todos?ticket=made-up"), collector);

        Throwable failure = null;
        try {
            attempt.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            failure = e instanceof ExecutionException ? e.getCause() : e;
        }
        assertThat(failure).isNotNull();
    }

    @Test
    void webSocketFromAForeignOriginIsRejected() throws Exception {
        String alice = api.registerAndLogin(aliceEmail).access();
        String ticket = newTicket(alice);
        CompletableFuture<WebSocket> attempt = api.http().newWebSocketBuilder()
                .header("Origin", "https://evil.example")
                .buildAsync(URI.create("ws://localhost:" + port + "/ws/todos?ticket=" + ticket), new Collector());

        Throwable failure = null;
        try {
            attempt.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            failure = e instanceof ExecutionException ? e.getCause() : e;
        }
        assertThat(failure).isNotNull();
    }
}
