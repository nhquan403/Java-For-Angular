package com.example.todo.application.service;

import com.example.todo.application.common.Actor;
import com.example.todo.application.common.AssistantEvent;
import com.example.todo.application.common.AssistantModelException;
import com.example.todo.application.common.InvalidAssistantRequestException;
import com.example.todo.application.common.PageQuery;
import com.example.todo.application.common.TooManyAssistantRequestsException;
import com.example.todo.application.port.in.ChatWithAssistantUseCase.ChatMessage;
import com.example.todo.application.port.in.ChatWithAssistantUseCase.Command;
import com.example.todo.application.port.in.ChatWithAssistantUseCase.Draft;
import com.example.todo.application.port.in.ChatWithAssistantUseCase.Outcome;
import com.example.todo.application.port.in.ChatWithAssistantUseCase.PageContext;
import com.example.todo.application.port.in.ChatWithAssistantUseCase.Role;
import com.example.todo.application.port.in.CreateTodoUseCase;
import com.example.todo.application.port.out.AssistantModelPort.ToolResult;
import com.example.todo.domain.model.Todo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.example.todo.application.service.ScriptedAssistantModel.call;
import static com.example.todo.application.service.ScriptedAssistantModel.refusal;
import static com.example.todo.application.service.ScriptedAssistantModel.say;
import static com.example.todo.application.service.ScriptedAssistantModel.text;
import static com.example.todo.application.service.ScriptedAssistantModel.tools;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssistantServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-08T10:00:00Z");
    private static final Actor ALICE = Actor.user(1L);
    private static final Actor BOB = Actor.user(2L);
    private static final Actor ADMIN = Actor.admin(99L);

    private TodoService todos;
    private ScriptedAssistantModel model;
    private AssistantService assistant;
    private final List<AssistantEvent> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        todos = new TodoService(new InMemoryTodoRepository(), new RecordingEventPublisher(), clock);
        model = new ScriptedAssistantModel();
        assistant = new AssistantService(todos, todos, model, new AssistantRateLimiter(100, clock));
    }

    private Todo create(Actor owner, String title) {
        return todos.create(new CreateTodoUseCase.Command(owner.userId(), title, null));
    }

    private static Command ask(Actor actor, String question) {
        return new Command(actor, List.of(new ChatMessage(Role.USER, question)), null);
    }

    private Outcome chat(Command command) {
        return assistant.start(command).deliver(event -> {
            events.add(event);
            return true;
        });
    }

    private static Map<String, Object> listInput(Boolean completed) {
        Map<String, Object> input = new HashMap<>();
        input.put("completed", completed);
        input.put("page", 0);
        input.put("size", 10);
        input.put("sortBy", "createdAt");
        input.put("direction", "desc");
        return input;
    }

    private ToolResult onlyToolResult() {
        assertThat(model.toolResultBatches).hasSize(1);
        assertThat(model.toolResultBatches.getFirst()).hasSize(1);
        return model.toolResultBatches.getFirst().getFirst();
    }

    // ------------------------------------------------------------------ thứ tự sự kiện

    @Test
    void emitsDeltaToolSuggestionAndDoneInOrder() {
        model.then(tools(say("Để mình xem danh sách."), call("t1", "list_todos", listInput(false))))
                .then(tools(call("t2", "suggest_todo",
                        Map.of("title", "  Chuẩn bị họp sprint ", "description", "Gom việc tồn đọng"))))
                .then(text("Mình đã gợi ý một todo."));

        Outcome outcome = chat(ask(ALICE, "Soạn giúp tôi todo \"chuẩn bị họp sprint\""));

        assertThat(events).containsExactly(
                new AssistantEvent.Delta("Để mình xem danh sách."),
                new AssistantEvent.ToolUse("list_todos"),
                new AssistantEvent.ToolUse("suggest_todo"),
                new AssistantEvent.Suggestion("Chuẩn bị họp sprint", "Gom việc tồn đọng"),
                new AssistantEvent.Delta("Mình đã gợi ý một todo."),
                new AssistantEvent.Done());
        assertThat(outcome).isEqualTo(new Outcome(Outcome.Status.COMPLETED, 2, 30, 15));
    }

    @Test
    void suggestTodoNeverWritesToTheDatabase() {
        model.then(tools(call("t1", "suggest_todo", Map.of("title", "Việc mới", "description", ""))))
                .then(text("Xong."));

        chat(ask(ALICE, "Gợi ý một việc"));

        assertThat(events).contains(new AssistantEvent.Suggestion("Việc mới", null));
        assertThat(todos.list(ADMIN, null, PageQuery.defaults())
                .totalElements()).isZero();
    }

    @Test
    void invalidSuggestionIsReportedToTheModelAndNotToTheClient() {
        model.then(tools(call("t1", "suggest_todo", Map.of("title", "x".repeat(101)))))
                .then(text("Tiêu đề dài quá."));

        chat(ask(ALICE, "Gợi ý"));

        assertThat(events).noneMatch(e -> e instanceof AssistantEvent.Suggestion);
        assertThat(onlyToolResult().error()).isTrue();
    }

    @Test
    void allToolResultsOfARoundGoBackInOneMessage() {
        Todo todo = create(ALICE, "Viết báo cáo");
        model.then(tools(call("a", "list_todos", listInput(null)), call("b", "get_todo", Map.of("id", todo.id()))))
                .then(text("Ok"));

        chat(ask(ALICE, "Có gì?"));

        assertThat(model.toolResultBatches).hasSize(1);
        assertThat(model.toolResultBatches.getFirst()).extracting(ToolResult::toolCallId).containsExactly("a", "b");
    }

    // ------------------------------------------------------------------ quyền đọc dữ liệu

    @Test
    void listTodosRunsWithTheCallersPermissions() {
        create(ALICE, "Việc của Alice");
        create(BOB, "Việc của Bob");
        model.then(tools(call("t1", "list_todos", listInput(null)))).then(text("ok"));

        chat(ask(ALICE, "Tóm tắt các việc"));

        String result = onlyToolResult().content();
        assertThat(result).contains("Việc của Alice").doesNotContain("Việc của Bob")
                .contains("\"totalElements\":1");
    }

    @Test
    void adminListsEveryonesTodos() {
        create(ALICE, "Việc của Alice");
        create(BOB, "Việc của Bob");
        model.then(tools(call("t1", "list_todos", listInput(null)))).then(text("ok"));

        chat(ask(ADMIN, "Tóm tắt"));

        assertThat(onlyToolResult().content()).contains("Việc của Alice").contains("Việc của Bob");
    }

    @Test
    void listTodosFiltersAndCapsThePageSize() {
        create(ALICE, "Đã xong");
        todos.complete(ALICE, 1L);
        create(ALICE, "Chưa xong");
        Map<String, Object> input = listInput(false);
        input.put("size", 500);
        model.then(tools(call("t1", "list_todos", input))).then(text("ok"));

        chat(ask(ALICE, "Việc chưa xong?"));

        String result = onlyToolResult().content();
        assertThat(result).contains("Chưa xong").doesNotContain("Đã xong").contains("\"size\":50");
    }

    @Test
    void userCannotReadSomeoneElsesTodoThroughGetTodo() {
        Todo bobs = create(BOB, "Bí mật của Bob");
        model.then(tools(call("t1", "get_todo", Map.of("id", bobs.id())))).then(text("Không tìm thấy."));

        chat(ask(ALICE, "Xem todo " + bobs.id()));

        ToolResult result = onlyToolResult();
        assertThat(result.error()).isTrue();
        assertThat(result.content()).doesNotContain("Bí mật").contains("Không tìm thấy todo có id " + bobs.id());
    }

    @Test
    void someoneElsesTodoLooksExactlyLikeAMissingOne() {
        Todo bobs = create(BOB, "Của Bob");
        long missing = 12345L;
        model.then(tools(call("a", "get_todo", Map.of("id", bobs.id())), call("b", "get_todo", Map.of("id", missing))))
                .then(text("ok"));

        chat(ask(ALICE, "Xem"));

        List<ToolResult> results = model.toolResultBatches.getFirst();
        assertThat(results.get(0).content().replace(bobs.id().toString(), "ID"))
                .isEqualTo(results.get(1).content().replace(Long.toString(missing), "ID"));
    }

    @Test
    void ownerCanReadTheirTodoThroughGetTodo() {
        Todo todo = create(ALICE, "Đi chợ");
        model.then(tools(call("t1", "get_todo", Map.of("id", todo.id())))).then(text("ok"));

        chat(ask(ALICE, "Todo này là gì?"));

        assertThat(onlyToolResult().error()).isFalse();
        assertThat(onlyToolResult().content()).contains("\"title\":\"Đi chợ\"");
    }

    @Test
    void noToolCanCreateUpdateOrDelete() {
        model.then(text("Chào bạn"));
        chat(ask(ALICE, "Chào"));
        assertThat(model.tools).extracting(t -> t.name())
                .containsExactly("list_todos", "get_todo", "suggest_todo");
        assertThat(model.tools).allSatisfy(tool ->
                assertThat(tool.inputSchema()).containsEntry("additionalProperties", false));
    }

    // ------------------------------------------------------------------ giới hạn và lỗi

    @Test
    void stopsAfterSixToolRounds() {
        model.forever(tools(call("t", "list_todos", listInput(null))));

        Outcome outcome = chat(ask(ALICE, "Lặp mãi"));

        assertThat(model.toolResultBatches).hasSize(AssistantService.MAX_TOOL_ROUNDS);
        assertThat(model.calls).isEqualTo(AssistantService.MAX_TOOL_ROUNDS + 1);
        assertThat(events.getLast()).isEqualTo(new AssistantEvent.Error(AssistantService.TOO_MANY_ROUNDS_MESSAGE));
        assertThat(events).doesNotContain(new AssistantEvent.Done());
        assertThat(outcome.status()).isEqualTo(Outcome.Status.TOO_MANY_TOOL_ROUNDS);
        assertThat(outcome.toolRounds()).isEqualTo(6);
    }

    @Test
    void refusalBecomesAnErrorEvent() {
        model.then(refusal());

        Outcome outcome = chat(ask(ALICE, "..."));

        assertThat(events).containsExactly(new AssistantEvent.Error(AssistantService.REFUSAL_MESSAGE));
        assertThat(outcome.status()).isEqualTo(Outcome.Status.REFUSED);
    }

    @Test
    void modelFailureAfterStreamingStartedBecomesAnErrorEvent() {
        model.then(tools(call("t1", "list_todos", listInput(null)))).thenFail();

        Outcome outcome = chat(ask(ALICE, "Tóm tắt"));

        assertThat(events).containsExactly(
                new AssistantEvent.ToolUse("list_todos"),
                new AssistantEvent.Error(AssistantService.MODEL_ERROR_MESSAGE));
        assertThat(outcome.status()).isEqualTo(Outcome.Status.MODEL_ERROR);
    }

    @Test
    void modelFailureOnTheFirstCallIsThrownBeforeStreaming() {
        model.thenFail();

        assertThatThrownBy(() -> assistant.start(ask(ALICE, "Chào")))
                .isInstanceOf(AssistantModelException.class);
    }

    @Test
    void stopsCallingTheModelWhenTheClientDisconnects() {
        model.then(tools(say("Đang xem..."), call("t1", "list_todos", listInput(null))))
                .then(text("không bao giờ tới đây"));

        Outcome outcome = assistant.start(ask(ALICE, "Tóm tắt")).deliver(event -> false);

        assertThat(model.calls).isEqualTo(1);
        assertThat(model.toolResultBatches).isEmpty();
        assertThat(outcome.status()).isEqualTo(Outcome.Status.CANCELLED);
    }

    @Test
    void rateLimitIsPerUser() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        AssistantService limited = new AssistantService(todos, todos,
                new ScriptedAssistantModel().forever(text("ok")), new AssistantRateLimiter(2, clock));

        limited.start(ask(ALICE, "1"));
        limited.start(ask(ALICE, "2"));
        assertThatThrownBy(() -> limited.start(ask(ALICE, "3")))
                .isInstanceOf(TooManyAssistantRequestsException.class);
        limited.start(ask(BOB, "1"));
    }

    @Test
    void rateLimitWindowSlides() {
        MutableClock clock = new MutableClock(NOW);
        AssistantRateLimiter limiter = new AssistantRateLimiter(1, clock);

        limiter.acquire(1L);
        assertThatThrownBy(() -> limiter.acquire(1L))
                .isInstanceOfSatisfying(TooManyAssistantRequestsException.class,
                        e -> assertThat(e.retryAfterSeconds()).isBetween(1L, 61L));
        clock.advance(java.time.Duration.ofSeconds(61));
        limiter.acquire(1L);
    }

    // ------------------------------------------------------------------ system prompt và lịch sử

    @Test
    void pageContextGoesIntoTheSystemPromptButTodoDataDoesNot() {
        create(ALICE, "Dữ liệu riêng tư");
        model.then(text("ok"));
        PageContext context = new PageContext("todo-detail", "/todos/42", null, 42L,
                new Draft("Đang gõ \"dở\"", null));

        chat(new Command(ALICE, List.of(new ChatMessage(Role.USER, "Todo này là gì?")), context));

        assertThat(model.systemPrompt)
                .contains("<page_context>")
                .contains("\"page\":\"todo-detail\"")
                .contains("\"todoId\":42")
                .contains("\"title\":\"Đang gõ \\\"dở\\\"\"")
                .doesNotContain("Dữ liệu riêng tư");
        assertThat(model.history).extracting(ChatMessage::content).containsExactly("Todo này là gì?");
    }

    @Test
    void draftIsTruncatedInsteadOfRejected() {
        model.then(text("ok"));
        PageContext context = new PageContext("todo-list", "/todos", null, null,
                new Draft("t".repeat(300), "d".repeat(900)));

        chat(new Command(ALICE, List.of(new ChatMessage(Role.USER, "?")), context));

        assertThat(model.systemPrompt).contains("t".repeat(100)).doesNotContain("t".repeat(101))
                .contains("d".repeat(500)).doesNotContain("d".repeat(501));
    }

    // ------------------------------------------------------------------ kiểm tra yêu cầu

    @Test
    void rejectsInvalidCommands() {
        ChatMessage user = new ChatMessage(Role.USER, "hi");
        ChatMessage bot = new ChatMessage(Role.ASSISTANT, "hello");

        assertThatThrownBy(() -> new Command(ALICE, List.of(), null))
                .isInstanceOf(InvalidAssistantRequestException.class);
        assertThatThrownBy(() -> new Command(ALICE, Collections.nCopies(21, user), null))
                .isInstanceOf(InvalidAssistantRequestException.class);
        assertThatThrownBy(() -> new Command(ALICE, List.of(bot, user), null))
                .isInstanceOf(InvalidAssistantRequestException.class);
        assertThatThrownBy(() -> new Command(ALICE, List.of(user, bot), null))
                .isInstanceOf(InvalidAssistantRequestException.class);
        assertThatThrownBy(() -> new ChatMessage(Role.USER, "x".repeat(4001)))
                .isInstanceOf(InvalidAssistantRequestException.class);
        assertThatThrownBy(() -> Role.fromApiName("system"))
                .isInstanceOf(InvalidAssistantRequestException.class);
        assertThatThrownBy(() -> new PageContext("settings", null, null, null, null))
                .isInstanceOf(InvalidAssistantRequestException.class);

        new Command(ALICE, Collections.nCopies(20, user), null);
        new ChatMessage(Role.USER, "x".repeat(4000));
    }
}
