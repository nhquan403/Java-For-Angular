# Todo API - Spring Boot 4.1 + Hexagonal Architecture

Yêu cầu: **JDK 21**, **Maven 3.9+**. Chạy bằng Docker thì chỉ cần **Docker**.

## Chạy

```bash
# 1. Chạy local, dùng H2 trong bộ nhớ (profile dev, mặc định)
mvn spring-boot:run

# 2. Chạy app + PostgreSQL bằng Docker (profile prod)
docker compose up --build

# 3. Chạy toàn bộ test
mvn verify
```

| Đường dẫn | Dùng cho |
|---|---|
| `http://localhost:8080/api/auth/...` | Đăng ký, đăng nhập (công khai) |
| `http://localhost:8080/api/todos` | API todo (cần đăng nhập) |
| `http://localhost:8080/api/assistant/chat` | Trợ lý AI dạng chat (cần đăng nhập, cần `ANTHROPIC_API_KEY`) |
| `http://localhost:8080/swagger-ui.html` | Tài liệu API (chỉ profile dev) |
| `http://localhost:8080/actuator/health` | Kiểm tra app còn sống |

## Những thứ đã có như dự án thực tế

| Hạng mục | Nằm ở đâu |
|---|---|
| PostgreSQL + Docker | `docker-compose.yml`, `Dockerfile` |
| Quản lý migration (Flyway) | `src/main/resources/db/migration/V1` đến `V4` |
| Đăng nhập JWT (access token) | `JwtTokenAdapter`, `JwtConfig`, `SecurityConfig` |
| Refresh token xoay vòng + phát hiện bị đánh cắp | `AuthService.refresh`, `RefreshToken` |
| Phân quyền theo role USER / ADMIN | `SecurityConfig`, `UserService`, `AdminUserController` |
| Todo của ai người nấy | `TodoService.findAccessible`, `Actor` |
| Băm mật khẩu BCrypt | `BcryptPasswordHasher` |
| Lỗi 401 / 403 cùng định dạng RFC 9457 | `ProblemJsonSecurityHandlers` |
| Dọn refresh token hết hạn mỗi giờ | `RefreshTokenCleanupJob` |
| Sự kiện domain khi todo thay đổi | `TodoEvent`, `TodoService`, `TodoEventPublisherPort` |
| Kafka + transactional outbox | `OutboxTodoEventPublisher`, `OutboxRelay`, `TodoEventKafkaListener`, migration `V4` |
| Đẩy thời gian thực qua SSE | `RealtimeController` (`GET /api/realtime/stream`) |
| Đẩy thời gian thực qua WebSocket | `TodoWebSocketHandler` (`/ws/todos`), `WebSocketConfig` |
| Xác thực SSE/WebSocket bằng ticket dùng một lần | `RealtimeTicketStore`, `TicketAuthenticationFilter` |
| Chống client chậm, giới hạn kết nối, heartbeat | `OutboundPump`, `RealtimeService` |
| Profile dev / prod | `application-dev.properties`, `application-prod.properties` |
| Phân trang + sắp xếp | `PageQuery`, `PageResult`, `TodoController.list` |
| Giao dịch (`@Transactional`) | `TodoService` |
| Chống ghi đè đồng thời (`@Version`) | `Todo.version`, `TodoJpaEntity`, lỗi 409 |
| Xử lý lỗi chuẩn RFC 9457 | `GlobalExceptionHandler` |
| Log có request id | `CorrelationIdFilter` |
| Health check (Actuator) | `/actuator/health` |
| Tài liệu API (OpenAPI) | `OpenApiConfig` |
| CORS | `WebConfig`, `app.cors.allowed-origins` |
| Trợ lý AI (Claude) trả lời qua SSE, đọc todo bằng tool | `AssistantService`, `AssistantController`, `AnthropicAssistantModelAdapter` |
| Test tích hợp | `TodoApiIntegrationTest` |
| Test kiến trúc (ArchUnit) | `ArchitectureTest` |
| CI (GitHub Actions) | `.github/workflows/ci.yml` |

**Chưa có:** cache, rate limit và chống dò mật khẩu (xem mục Giới hạn bên dưới), Testcontainers, xác thực email, quên mật khẩu, khóa/xóa tài khoản.

## Kiến trúc Hexagonal (Ports & Adapters)

Lõi nghiệp vụ ở giữa, không biết gì về HTTP hay database. Thế giới bên ngoài nói chuyện với lõi qua "cổng" (port) bằng các "bộ chuyển đổi" (adapter).

```
 HTTP request
      |
      v
[ adapter/in/web ]  --gọi-->  [ port/in ]  <--hiện thực--  [ application/service ]
   TodoController               UseCase                         TodoService
                                                                     |
                                                                  dùng
                                                                     v
                                                              [ domain/model ]  Todo
                                                                     |
                                                              gọi qua port/out
                                                                     v
 [ adapter/out/persistence ]  <--hiện thực--  [ port/out ]  TodoRepositoryPort
   TodoPersistenceAdapter (JPA + Flyway, PostgreSQL / H2)
```

Quy tắc: mũi tên chỉ vào trong. `domain` không import gì ngoài Java. `application` chỉ import `domain`, Java chuẩn và `@Transactional`. `ArchitectureTest` kiểm tra tự động.

Phần bảo mật đi theo cùng nguyên tắc: lõi chỉ biết cổng `PasswordHasherPort` và `TokenPort`. BCrypt và JWT nằm ở `adapter/out/security`, đổi sang Argon2 hay PASETO thì lõi không đổi. Lõi cũng không biết Spring Security: adapter web đọc token rồi đổi thành `Actor(userId, admin)` trước khi gọi use case.

```
src/main/java/com/example/todo/
├── domain/                      LÕI nghiệp vụ thuần Java
│   ├── event/                     TodoEvent
│   ├── model/                     Todo, User, Role, RefreshToken
│   └── exception/                 NotFound, Invalid, Conflict, InvalidCredentials, ...
├── application/
│   ├── common/                    Actor, AuthTokens, PageQuery, PageResult, ...
│   ├── port/in/                   use case todo, auth, quản lý user
│   ├── port/out/                  TodoRepositoryPort, UserRepositoryPort, RefreshTokenRepositoryPort,
│   │                              PasswordHasherPort, TokenPort, AssistantModelPort
│   └── service/                   TodoService, AuthService, UserService, RealtimeService, AssistantService
├── adapter/
│   ├── in/web/                  TodoController, AuthController, AdminUserController,
│   │                              GlobalExceptionHandler, ProblemJsonSecurityHandlers, CorrelationIdFilter, dto/
│   ├── in/realtime/             RealtimeController (SSE + ticket), TodoWebSocketHandler, OutboundPump,
│   │                              RealtimeTicketStore, TicketAuthenticationFilter, RealtimeProperties
│   ├── in/messaging/            TodoEventKafkaListener (Kafka là "người gọi")
│   ├── in/scheduler/            RefreshTokenCleanupJob, TicketPurgeJob
│   ├── out/persistence/         JpaEntity + SpringData repository + PersistenceAdapter cho todo, user, refresh token
│   ├── out/messaging/           OutboxTodoEventPublisher, OutboxRelay, InProcessTodoEventPublisher, TodoEventJson
│   ├── out/anthropic/           AnthropicAssistantModelAdapter (gọi Claude bằng Claude Java SDK)
│   └── out/security/            BcryptPasswordHasher, JwtTokenAdapter
├── config/                      BeanConfig, SecurityConfig, JwtConfig, WebConfig (CORS), OpenApiConfig, SchedulingConfig,
│                                  RealtimeConfig, WebSocketConfig, KafkaConfig
└── TodoApplication.java

src/main/resources/
├── application.properties         cấu hình chung (hạn token, issuer, BCrypt)
├── application-dev.properties     H2, log SQL, khóa JWT và admin mặc định CHỈ DÙNG DEV
├── application-prod.properties    PostgreSQL, JWT_SECRET và admin đầu tiên qua biến môi trường
└── db/migration/                  V1 todos, V2 users + refresh_tokens, V3 todos.owner_id, V4 outbox_events
```

## Đăng nhập, role và refresh token

```
POST /api/auth/register  -->  tạo user (role USER)
POST /api/auth/login     -->  access token (JWT, 15 phút) + refresh token (chuỗi ngẫu nhiên, 7 ngày)
Mọi API khác             -->  Authorization: Bearer <access token>
POST /api/auth/refresh   -->  đổi refresh token lấy cặp mới (token cũ bị thu hồi)
POST /api/auth/logout    -->  thu hồi refresh token
```

- **Access token** là JWT ký HS256, chứa `sub` (id user), `email`, `roles`. Sống ngắn nên lộ ra cũng ít hại. Server không lưu, chỉ kiểm tra chữ ký và hạn dùng.
- **Refresh token** là 256 bit ngẫu nhiên, không phải JWT. Database chỉ lưu bản băm SHA-256, nên lộ database cũng không dùng được. Mỗi token dùng được **một lần** (xoay vòng).
- **Phát hiện đánh cắp:** nếu một token đã dùng rồi mà có người đem ra dùng lại, server thu hồi toàn bộ refresh token của user đó, buộc đăng nhập lại.
- **Role:** `USER` chỉ thấy và sửa todo của mình (todo người khác trả 404, không phải 403, để không dò được id). `ADMIN` thấy tất cả và vào được `/api/admin/**`.
- Quy tắc sở hữu và quyền ADMIN nằm trong lõi (`TodoService`, `UserService`), không chỉ ở cấu hình URL.
- **Đổi role:** ADMIN đổi role của người khác bằng `PATCH /api/admin/users/{id}/role`. Refresh token của người đó bị thu hồi, còn access token cũ vẫn mang role cũ cho đến khi hết hạn (tối đa 15 phút).

### Cấu hình

| Biến / thuộc tính | Ý nghĩa |
|---|---|
| `JWT_SECRET` (`app.security.jwt.secret`) | Khóa ký, tối thiểu 32 ký tự, **bắt buộc ở prod**. Tạo: `openssl rand -base64 48` |
| `app.security.jwt.access-token-ttl` | Mặc định `15m` |
| `app.security.jwt.refresh-token-ttl` | Mặc định `7d` |
| `app.security.bcrypt-strength` | Mặc định `10`, test dùng `4` |
| `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD` | Tạo tài khoản ADMIN đầu tiên khi khởi động nếu email chưa tồn tại. Xóa mật khẩu khỏi môi trường sau lần đầu |

Profile dev có sẵn khóa JWT và tài khoản `admin@example.com` / `Admin#12345` để học cho tiện. Ai đọc mã nguồn cũng biết, **tuyệt đối không dùng ở production**.

## Thời gian thực: Kafka, SSE, WebSocket

Khi todo được tạo, sửa, hoàn thành, mở lại hoặc xóa, app phát một **sự kiện**. Client đang mở trang nhận nó ngay, không cần tải lại. USER chỉ nhận sự kiện todo của mình, ADMIN nhận tất cả.

```
TodoService (use case)
   | publish(TodoEvent)  <-- cổng ra TodoEventPublisherPort, nằm TRONG giao dịch
   v
 ┌─ Kafka tắt (dev, mặc định) ───────────────┐   ┌─ Kafka bật (production) ─────────────────────────────┐
 │ InProcessTodoEventPublisher               │   │ OutboxTodoEventPublisher  ghi bảng outbox_events      │
 │ giao thẳng SAU KHI commit                 │   │ cùng giao dịch với todo                               │
 │ (chỉ đủ cho 1 instance)                   │   │   -> OutboxRelay đẩy sang Kafka (topic todo-events)   │
 └──────────────────┬────────────────────────┘   │   -> TodoEventKafkaListener ở MỌI instance nhận       │
                    |                            └─────────────────────┬─────────────────────────────────┘
                    └───────────────► NotifyTodoEventUseCase ◄─────────┘
                                          |  RealtimeService: ai được nhận? (chủ todo + ADMIN)
                                          v
                              OutboundPump (mỗi client một hàng đợi + một luồng ghi)
                                |                          |
                          SSE  GET /api/realtime/stream     WebSocket /ws/todos
```

**Vì sao có bảng outbox thay vì gọi `kafkaTemplate.send` thẳng?** Database và Kafka là hai hệ thống, không có giao dịch chung. Gửi Kafka xong mà database rollback thì có sự kiện ma, database commit xong mà Kafka lỗi thì mất sự kiện. Ghi sự kiện vào outbox cùng giao dịch với todo thì hoặc có cả hai hoặc không có gì; `OutboxRelay` lo đẩy sang Kafka sau. Bảo đảm "ít nhất một lần" (có thể trùng, không mất).

**Vì sao mỗi instance một consumer group riêng?** Để MỌI instance đều nhận MỌI sự kiện. Nếu dùng chung một group, Kafka chia sự kiện cho các instance, và client nối vào instance không được chia sẽ không nhận được gì.

### Dùng từ trình duyệt

Trình duyệt không gắn được header `Authorization` vào `EventSource` hay `WebSocket`, nên cần **ticket**: một chuỗi ngẫu nhiên dùng một lần, sống 30 giây. Lấy ticket bằng request có Bearer token bình thường, rồi mở kết nối với `?ticket=`.

```js
// 1. Lấy ticket (đã đăng nhập, có access token)
const { ticket } = await fetch("/api/realtime/ticket", {
  method: "POST", headers: { Authorization: `Bearer ${accessToken}` },
}).then(r => r.json());

// 2a. SSE (tự kết nối lại khi rớt, nhưng mỗi lần kết nối lại cần ticket MỚI)
const es = new EventSource(`/api/realtime/stream?ticket=${ticket}`);
es.addEventListener("todo", e => console.log(JSON.parse(e.data)));
es.onerror = () => { es.close(); /* lấy ticket mới rồi mở lại */ };

// 2b. hoặc WebSocket
const ws = new WebSocket(`ws://localhost:8080/ws/todos?ticket=${ticket}`);
ws.onmessage = e => console.log(JSON.parse(e.data));
```

Mỗi sự kiện có dạng:

```json
{"type":"COMPLETED","todoId":5,"ownerId":1,"title":"Hoc Spring Boot","completed":true,"occurredAt":"2026-10-07T03:00:00Z"}
```

`type` là một trong `CREATED`, `UPDATED`, `COMPLETED`, `REOPENED`, `DELETED`. Sự kiện chỉ để biết "có gì đổi", cần dữ liệu đầy đủ thì gọi `GET /api/todos/{id}`. Client không phải trình duyệt (curl, ứng dụng di động) gửi thẳng header `Authorization: Bearer ...` được, không cần ticket.

Thử bằng curl:

```bash
TICKET=$(curl -s -X POST -H "Authorization: Bearer $TOKEN" localhost:8080/api/realtime/ticket | sed 's/.*"ticket":"\([^"]*\)".*/\1/')
curl -N "localhost:8080/api/realtime/stream?ticket=$TICKET"
# mở terminal khác, tạo hoặc sửa một todo, sự kiện hiện ra ở terminal này
```

### Cấu hình

| Thuộc tính | Mặc định | Ý nghĩa |
|---|---|---|
| `app.kafka.enabled` | `false` (prod: `true`) | Bật Kafka + outbox. Bật thì cần PostgreSQL (dùng advisory lock) |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Địa chỉ broker (compose đặt `kafka:19092`) |
| `app.kafka.topic` / `partitions` / `replicas` | `todo-events` / 3 / 1 | Topic. Khóa bản ghi là `ownerId` nên sự kiện của một người giữ đúng thứ tự |
| `app.kafka.outbox.poll-interval-ms` / `batch-size` | 200 / 200 | Nhịp và cỡ lô của `OutboxRelay` |
| `app.realtime.max-connections-per-user` | 5 | Quá số này nhận 429 (SSE) hoặc đóng với mã 1013 (WebSocket) |
| `app.realtime.queue-capacity` | 256 | Hàng đợi mỗi client. Đầy nghĩa là client quá chậm, bị ngắt |
| `app.realtime.heartbeat-interval` | 25s | Gửi tín hiệu giữ sống khi im lặng (giữ kết nối qua proxy) |
| `app.realtime.max-connection-age` | 15m | Hết tuổi thì server đóng, client xin ticket mới và nối lại |
| `app.realtime.ticket-ttl` | 30s | Hạn của ticket |
| `app.cors.allowed-origins` | | Cũng là danh sách `Origin` được phép mở WebSocket |

Chạy cả hệ thống có Kafka: `docker compose up --build` (compose đã có dịch vụ `kafka` chế độ KRaft, không cần ZooKeeper).

### Những điểm tối ưu và an toàn đáng học

- **`OutboundPump`:** sự kiện được giao từ luồng của Kafka, nếu ghi thẳng xuống mạng thì một client chậm làm tắc mọi client khác. Mỗi client có hàng đợi giới hạn và một luồng ảo (virtual thread) riêng; đầy thì ngắt client đó thay vì để bộ nhớ phình ra.
- **Giao sau khi commit** (chế độ không Kafka): giao ngay trong giao dịch rồi rollback sẽ làm client nhận sự kiện về thay đổi chưa từng xảy ra.
- **Chỉ một instance chạy relay** (advisory lock của PostgreSQL), giữ đúng thứ tự; gửi cả lô không đồng bộ rồi mới chờ kết quả, Kafka gộp thành vài request.
- **Kiểm tra `Origin` của WebSocket:** WebSocket không bị CORS chặn, trang web lạ nào cũng mở được kết nối tới server của bạn nếu server không tự kiểm tra (tấn công cross-site WebSocket hijacking).
- **Ticket dùng một lần:** đưa token lên URL thì lộ trong log; ticket lộ cũng vô dụng sau lần dùng đầu và sau 30 giây.
- **Tuổi thọ kết nối bằng hạn access token:** quyền của người dùng không "sống" lâu hơn token.
- **Gói tin nhỏ:** sự kiện chỉ mang 6 trường; JSON viết tay có test cho phần thoát ký tự (title không thể chèn thêm trường giả).

## Trợ lý AI (mini chat)

`POST /api/assistant/chat` gửi câu hỏi cho Claude và nhận câu trả lời dạng **Server-Sent Events**. Trợ lý đọc được todo của người đang đăng nhập bằng tool và gợi ý todo mới để frontend điền vào form tạo. Trợ lý **không** tạo, sửa hay xóa dữ liệu: người dùng tự bấm "Thêm".

```
FE --POST /api/assistant/chat--> AssistantController (adapter/in/web)
                                   | đọc user từ token, kiểm tra, rate limit, gọi Claude vòng đầu
                                   v
                                AssistantService (application)  --tool--> ListTodosUseCase / GetTodoUseCase
                                   |                                       (quyền của người gọi, như GET /api/todos)
                                   v
                                AssistantModelPort --> AnthropicAssistantModelAdapter (adapter/out/anthropic) --> Claude
```

### Bật tính năng

| Biến / thuộc tính | Mặc định | Ý nghĩa |
|---|---|---|
| `ANTHROPIC_API_KEY` (biến môi trường) | không có | **Bắt buộc.** Thiếu thì endpoint không được đăng ký (404, frontend hiện "trợ lý chưa được bật"), app vẫn chạy bình thường. Không ghi key vào file cấu hình |
| `app.assistant.enabled` | `true` | `false` thì tắt hẳn (404) kể cả khi có key |
| `app.assistant.model` | `claude-opus-5-5` | Model Claude |
| `app.assistant.max-tokens` | `16000` | Token tối đa của một vòng trả lời, gồm cả phần suy nghĩ |
| `app.assistant.rate-limit-per-minute` | `20` | Số câu hỏi tối đa của một người trong một phút, vượt thì 429 |
| `app.assistant.request-timeout` | `60s` | Chờ tối đa một lần gọi Claude |
| `app.assistant.stream-timeout` | `5m` | Thời gian tối đa của cả một câu trả lời |

```bash
export ANTHROPIC_API_KEY=sk-ant-...      # lấy ở console.anthropic.com
mvn spring-boot:run
# Docker: đặt ANTHROPIC_API_KEY trong file .env (xem .env.example), docker-compose.yml đã chuyển nó vào app
```

Frontend Angular chạy ở `http://localhost:4200` thì domain đó phải có trong `app.cors.allowed-origins` (mặc định chỉ có 3000 và 5173), hoặc frontend gọi qua proxy của `ng serve`. `/api/assistant/**` dùng chung CORS và bảo mật Bearer với `/api/todos`.

### Hợp đồng với frontend

Request (`Accept: text/event-stream`):

```json
{
  "messages": [ { "role": "user", "content": "Tóm tắt các việc chưa xong" } ],
  "context": {
    "page": "todo-list", "path": "/todos",
    "query": { "completed": false, "page": 0, "size": 10, "sortBy": "createdAt", "direction": "desc" },
    "todoId": null, "createDraft": null
  }
}
```

Response 200 `text/event-stream`, mỗi sự kiện là `event: <tên>`, `data: <JSON>` rồi một dòng trống:

| Sự kiện | Dữ liệu | Ý nghĩa |
|---|---|---|
| `delta` | `{"text": "..."}` | Một phần câu trả lời, nối dần |
| `tool` | `{"name": "list_todos" \| "get_todo" \| "suggest_todo"}` | Trợ lý đang dùng tool |
| `suggestion` | `{"title": "...", "description": "..." \| null}` | Gợi ý todo, frontend hiện nút "Điền vào form tạo" |
| `done` | `{}` | Kết thúc |
| `error` | `{"message": "..."}` | Lỗi sau khi đã bắt đầu trả lời (tiếng Việt). Luồng đóng luôn, không có `done` |

Lỗi trước khi bắt đầu trả lời là HTTP status + ProblemDetail: 400 (yêu cầu sai), 401, 429 (quá giới hạn, có `Retry-After`), 503 (Claude lỗi ngay vòng đầu). Client ngắt kết nối (nút Dừng) thì server dừng gọi Claude ở lần ghi kế tiếp.

```bash
curl -N -X POST localhost:8080/api/assistant/chat -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -H "Accept: text/event-stream" \
  -d '{"messages":[{"role":"user","content":"Tóm tắt các việc chưa xong"}],"context":null}'
```

### Giới hạn

- Tối đa 20 tin mỗi request, mỗi tin tối đa 4000 ký tự; `role` chỉ là `user` hoặc `assistant`; tin đầu và tin cuối phải là `user` (tin cuối là `assistant` thì model hiểu là "viết tiếp" và từ chối).
- Tối đa 6 vòng tool cho một câu hỏi; cần thêm thì dừng và trả sự kiện `error`. `list_todos` trả tối đa 50 todo mỗi lần.
- Rate limit giữ trong bộ nhớ, mỗi instance đếm riêng: chạy N instance thì giới hạn thực tế gấp N.
- Không lưu lịch sử chat: frontend gửi lại toàn bộ hội thoại mỗi lần hỏi.
- Mỗi vòng gọi Claude không stream: mỗi khối văn bản thành một sự kiện `delta` (không phải từng chữ). Vòng đầu chạy trên luồng request để trả được 503 đúng nghĩa, nên header 200 về sau khi Claude trả lời vòng đầu.
- Log chỉ ghi user id, số vòng tool, số token và request id; không ghi nội dung hội thoại.

### Chi phí cần lưu ý

- Claude Opus 5.5 tính **$4 / 1 triệu token đầu vào** và **$20 / 1 triệu token đầu ra** (giá tại thời điểm viết, xem anthropic.com/pricing). Phần suy nghĩ (thinking) của model tính như token đầu ra; effort đặt `LOW` để giữ ngắn.
- Mỗi lần gọi tool là thêm một lượt gọi Claude, và mỗi lượt gửi lại toàn bộ hội thoại + kết quả tool. Một câu hỏi thường tốn 1 đến 3 lượt, vài nghìn token đầu vào, ước chừng vài xu Mỹ. Hội thoại dài (20 tin x 4000 ký tự) có thể tốn gấp nhiều lần.
- Theo dõi bằng dòng log `Assistant reply: ... inputTokens=... outputTokens=...` và đặt giới hạn chi tiêu (spend limit) trong Anthropic Console. Giảm `app.assistant.rate-limit-per-minute` nếu cần.

## Endpoint

| Method | URL | Quyền | Mô tả |
|---|---|---|---|
| POST | `/api/auth/register` | công khai | Đăng ký (201) |
| POST | `/api/auth/login` | công khai | Đăng nhập, nhận cặp token |
| POST | `/api/auth/refresh` | công khai | Đổi refresh token lấy cặp mới |
| POST | `/api/auth/logout` | công khai | Thu hồi refresh token (luôn 204) |
| GET | `/api/auth/me` | đăng nhập | Thông tin của mình |
| POST | `/api/todos` | đăng nhập | Tạo todo (201 + `Location`) |
| GET | `/api/todos` | đăng nhập | Danh sách phân trang (của mình, ADMIN thì của tất cả) |
| GET | `/api/todos/{id}` | đăng nhập | Xem một todo |
| PUT | `/api/todos/{id}` | đăng nhập | Sửa (nên gửi kèm `version`) |
| PATCH | `/api/todos/{id}/complete` | đăng nhập | Đánh dấu hoàn thành |
| PATCH | `/api/todos/{id}/reopen` | đăng nhập | Mở lại |
| DELETE | `/api/todos/{id}` | đăng nhập | Xóa (204) |
| POST | `/api/assistant/chat` | đăng nhập | Hỏi trợ lý AI, trả lời dạng SSE (404 nếu chưa bật) |
| POST | `/api/realtime/ticket` | đăng nhập | Lấy ticket dùng một lần để mở SSE/WebSocket |
| GET | `/api/realtime/stream` | đăng nhập hoặc `?ticket=` | Luồng sự kiện SSE |
| GET | `/ws/todos` | đăng nhập hoặc `?ticket=` | WebSocket nhận sự kiện (giao thức `ws://`) |
| GET | `/api/admin/users` | ADMIN | Danh sách user (`page`, `size`) |
| PATCH | `/api/admin/users/{id}/role` | ADMIN | Đổi role, body `{"role":"ADMIN"}` |

Tham số danh sách: `completed`, `page` (từ 0), `size` (1..100, mặc định 20), `sortBy` (`id|title|createdAt|updatedAt`), `direction` (`asc|desc`).

## Mã lỗi

| Mã | Khi nào |
|---|---|
| 400 | Dữ liệu sai: title rỗng, email hoặc mật khẩu không hợp lệ, JSON hỏng, id không phải số, tham số phân trang sai |
| 401 | Chưa gửi token, token sai, bị sửa hoặc hết hạn; đăng nhập sai; refresh token không hợp lệ |
| 403 | Đã đăng nhập nhưng không đủ quyền (USER gọi API của ADMIN) |
| 404 | Không có todo với id đó (hoặc todo của người khác), hoặc đường dẫn không tồn tại |
| 405 | Sai method |
| 409 | `version` gửi lên đã cũ, hoặc email đã được đăng ký |
| 429 | Mở quá nhiều kết nối thời gian thực, có quá nhiều ticket chờ dùng, hoặc hỏi trợ lý AI quá số lần mỗi phút |
| 500 | Lỗi bất ngờ, chi tiết chỉ ghi vào log, không lộ cho client |
| 503 | Trợ lý AI không gọi được Claude lúc bắt đầu trả lời |

## Thử bằng curl

```bash
# Đăng ký rồi đăng nhập (mật khẩu 8 ký tự trở lên)
curl -X POST localhost:8080/api/auth/register -H "Content-Type: application/json" \
  -d '{"email":"an@example.com","password":"matkhau123"}'
curl -X POST localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"email":"an@example.com","password":"matkhau123"}'
# Kết quả có "accessToken" và "refreshToken". Gán vào biến để dùng tiếp:
TOKEN=<dán accessToken>
REFRESH=<dán refreshToken>

curl -X POST localhost:8080/api/todos -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"title":"Hoc Spring Boot","description":"Hexagonal"}'

curl -H "Authorization: Bearer $TOKEN" \
  "localhost:8080/api/todos?completed=false&page=0&size=10&sortBy=title&direction=asc"
curl -X PATCH -H "Authorization: Bearer $TOKEN" localhost:8080/api/todos/1/complete

# Sửa kèm version (lấy từ lần GET trước). Gửi lại version cũ lần hai sẽ nhận 409.
curl -X PUT localhost:8080/api/todos/1 -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"title":"Hoc xong roi","version":1}'

# Access token hết hạn (15 phút) thì đổi lấy cặp mới. Mỗi refresh token chỉ dùng được một lần.
curl -X POST localhost:8080/api/auth/refresh -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$REFRESH\"}"

# Đăng xuất
curl -X POST localhost:8080/api/auth/logout -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$REFRESH\"}"

# Gắn request id của riêng bạn, nó sẽ xuất hiện trong log và header response
curl -i -H "X-Request-Id: my-trace-1" localhost:8080/actuator/health

# Thử quyền ADMIN (profile dev có sẵn tài khoản này)
curl -X POST localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"email":"admin@example.com","password":"Admin#12345"}'
curl -H "Authorization: Bearer $ADMIN_TOKEN" localhost:8080/api/admin/users
```

Trong Swagger UI (profile dev), bấm nút **Authorize** rồi dán access token để thử các API cần đăng nhập.

## Thêm thay đổi cấu trúc database

Không sửa các file `V1` đến `V3`. Tạo file mới, ví dụ `V4__add_due_date.sql`, Flyway sẽ chạy nó đúng một lần khi app khởi động. Nhớ thêm field tương ứng vào `TodoJpaEntity`, vì `ddl-auto=validate` sẽ báo lỗi nếu entity và bảng lệch nhau.

## Vì sao tách như vậy?

- `RealtimeServiceTest`, `OutboundPumpTest`, `RealtimeTicketStoreTest`, `TodoEventJsonTest` kiểm tra phần thời gian thực (ai nhận sự kiện nào, client chậm bị ngắt, ticket dùng một lần, thoát ký tự JSON) mà không cần Spring, Kafka hay mạng.
- `AssistantServiceTest` test trợ lý AI bằng mô hình giả `ScriptedAssistantModel` (thứ tự sự kiện, quyền đọc todo, giới hạn 6 vòng, lỗi và từ chối), không gọi Claude thật. `AssistantApiIntegrationTest` kiểm tra hợp đồng SSE qua HTTP.
- `TodoServiceTest`, `AuthServiceTest`, `UserServiceTest` test toàn bộ use case (kể cả xoay vòng token và phân quyền) bằng các lớp giả `InMemory...Repository`, không cần Spring hay database, chạy trong mili giây.
- Đổi PostgreSQL sang MongoDB: viết adapter mới cho `TodoRepositoryPort`, lõi không đổi.
- Thêm giao diện gọi use case (CLI, gRPC, message queue): thêm adapter vào, lõi không đổi.

## Giới hạn đã biết (dành cho bản học tập này)

- **Chưa chống dò mật khẩu:** không giới hạn số lần đăng nhập sai. Thực tế cần rate limit theo IP và email (bucket4j, hoặc ở API gateway) và khóa tạm tài khoản.
- **Refresh không có thời gian châm chước:** nếu mạng rớt đúng lúc client nhận token mới rồi thử lại bằng token cũ, server coi đó là dùng lại và thu hồi mọi phiên. Client phải lưu token mới ngay khi nhận được.
- **Đổi role có độ trễ:** access token cũ mang role cũ đến khi hết hạn (tối đa 15 phút).
- **Chưa có khóa hoặc xóa tài khoản, đổi mật khẩu, quên mật khẩu, xác thực email.**
- **Khóa JWT dùng chung (HS256):** hợp khi một ứng dụng vừa phát vừa kiểm tra token. Nhiều dịch vụ cùng kiểm tra thì nên chuyển sang RS256.
- Phía client nên lưu token ở nơi an toàn (không đặt refresh token trong `localStorage` của web nếu có thể tránh), và luôn dùng HTTPS ở production.
- **Thời gian thực chỉ "báo có thay đổi", không phát lại:** client rớt mạng sẽ lỡ sự kiện trong lúc mất kết nối. Nối lại thì nên tải lại danh sách. Muốn không lỡ phải thêm `Last-Event-ID` và lưu lịch sử sự kiện.
- **Ticket giữ trong bộ nhớ:** chạy nhiều instance thì ticket phải được dùng ở đúng instance đã phát (sticky session), hoặc chuyển kho ticket sang Redis.
- **Kafka outbox yêu cầu PostgreSQL** (advisory lock). Một instance duy nhất đẩy outbox tại một thời điểm, đủ cho hàng nghìn sự kiện mỗi giây; muốn hơn thì phân vùng outbox theo nhiều relay.
- **Kafka dùng PLAINTEXT, một node, không xác thực** trong compose: chỉ để học. Production cần TLS, SASL và ít nhất 3 broker.
- **JDK 21:** luồng ảo ghi mạng trong khối `synchronized` của Tomcat có thể ghim luồng nền khi client rất chậm. JDK 24 trở lên hết vấn đề này.
