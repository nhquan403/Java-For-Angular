package com.example.todo.application.service;

import com.example.todo.application.common.Actor;
import com.example.todo.application.common.AssistantEvent;
import com.example.todo.application.common.InvalidPageQueryException;
import com.example.todo.application.common.JsonText;
import com.example.todo.application.common.PageQuery;
import com.example.todo.application.common.PageResult;
import com.example.todo.application.port.in.GetTodoUseCase;
import com.example.todo.application.port.in.ListTodosUseCase;
import com.example.todo.application.port.out.AssistantModelPort.ToolCall;
import com.example.todo.application.port.out.AssistantModelPort.ToolResult;
import com.example.todo.application.port.out.AssistantModelPort.ToolSpec;
import com.example.todo.domain.exception.TodoNotFoundException;
import com.example.todo.domain.model.Todo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Các tool của trợ lý: định nghĩa (tên, mô tả, JSON Schema) và cách chạy.
 *
 * Tool chỉ ĐỌC, và đọc qua đúng use case của API todo với quyền của người đang đăng nhập:
 * USER chỉ thấy todo của mình, ADMIN thấy tất cả. suggest_todo không ghi database, chỉ trả về
 * một gợi ý để frontend điền form; người dùng tự bấm "Thêm".
 */
final class AssistantTools {

    static final String LIST_TODOS = "list_todos";
    static final String GET_TODO = "get_todo";
    static final String SUGGEST_TODO = "suggest_todo";

    /** Số todo tối đa mỗi lần list_todos, để kết quả gửi cho mô hình không quá dài. */
    static final int MAX_LIST_SIZE = 50;

    /** Kết quả chạy một tool: phần gửi lại cho mô hình, và gợi ý cần gửi cho client (nếu có). */
    record Execution(ToolResult result, AssistantEvent.Suggestion suggestion) {
    }

    private final ListTodosUseCase listTodos;
    private final GetTodoUseCase getTodo;

    AssistantTools(ListTodosUseCase listTodos, GetTodoUseCase getTodo) {
        this.listTodos = listTodos;
        this.getTodo = getTodo;
    }

    Execution execute(Actor actor, ToolCall call) {
        try {
            return switch (call.name()) {
                case LIST_TODOS -> new Execution(ok(call, listTodos(actor, call.input())), null);
                case GET_TODO -> new Execution(getTodo(actor, call), null);
                case SUGGEST_TODO -> suggestTodo(call);
                default -> new Execution(error(call, "Tool không tồn tại: " + call.name()), null);
            };
        } catch (InvalidPageQueryException | IllegalArgumentException e) {
            return new Execution(error(call, "Tham số không hợp lệ: " + e.getMessage()), null);
        }
    }

    // ------------------------------------------------------------------ từng tool

    private Map<String, Object> listTodos(Actor actor, Map<String, Object> input) {
        Boolean completed = input.get("completed") instanceof Boolean b ? b : null;
        int page = intParam(input, "page", 0);
        // Mô hình xin nhiều hơn giới hạn thì trả đúng giới hạn thay vì báo lỗi.
        int size = Math.clamp(intParam(input, "size", 10), 1, MAX_LIST_SIZE);
        PageQuery query = PageQuery.of(page, size,
                stringParam(input, "sortBy", "createdAt"), stringParam(input, "direction", "desc"));
        PageResult<Todo> result = listTodos.list(actor, completed, query);

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("page", result.page());
        json.put("size", result.size());
        json.put("totalElements", result.totalElements());
        json.put("totalPages", result.totalPages());
        json.put("todos", result.content().stream().map(AssistantTools::todoJson).toList());
        return json;
    }

    private ToolResult getTodo(Actor actor, ToolCall call) {
        if (!(call.input().get("id") instanceof Number number)) {
            return error(call, "Thiếu id hoặc id không phải số.");
        }
        long id = number.longValue();
        try {
            return ok(call, todoJson(getTodo.getById(actor, id)));
        } catch (TodoNotFoundException e) {
            // Todo của người khác cũng trả "không tìm thấy", không để lộ là nó có tồn tại.
            return error(call, "Không tìm thấy todo có id " + id + ".");
        }
    }

    private static Execution suggestTodo(ToolCall call) {
        String title = call.input().get("title") instanceof String s ? s.strip() : "";
        String description = call.input().get("description") instanceof String s && !s.isBlank() ? s.strip() : null;
        if (title.isEmpty() || title.length() > Todo.MAX_TITLE_LENGTH) {
            return new Execution(error(call, "title phải có từ 1 đến " + Todo.MAX_TITLE_LENGTH + " ký tự."), null);
        }
        if (description != null && description.length() > Todo.MAX_DESCRIPTION_LENGTH) {
            return new Execution(error(call, "description tối đa " + Todo.MAX_DESCRIPTION_LENGTH + " ký tự."), null);
        }
        ToolResult result = ok(call, Map.of("status",
                "Đã hiện gợi ý cho người dùng. Todo CHƯA được tạo; người dùng tự bấm để điền vào form tạo."));
        return new Execution(result, new AssistantEvent.Suggestion(title, description));
    }

    // ------------------------------------------------------------------ tiện ích

    private static ToolResult ok(ToolCall call, Object json) {
        return new ToolResult(call.id(), JsonText.write(json), false);
    }

    private static ToolResult error(ToolCall call, String message) {
        return new ToolResult(call.id(), JsonText.write(Map.of("error", message)), true);
    }

    private static int intParam(Map<String, Object> input, String name, int defaultValue) {
        Object value = input.get(name);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number && number.doubleValue() == Math.rint(number.doubleValue())) {
            return number.intValue();
        }
        throw new IllegalArgumentException(name + " must be an integer");
    }

    private static String stringParam(Map<String, Object> input, String name, String defaultValue) {
        return input.get(name) instanceof String s ? s : defaultValue;
    }

    private static Map<String, Object> todoJson(Todo todo) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", todo.id());
        json.put("title", todo.title());
        json.put("description", todo.description());
        json.put("completed", todo.completed());
        json.put("createdAt", todo.createdAt());
        json.put("updatedAt", todo.updatedAt());
        return json;
    }

    // ------------------------------------------------------------------ định nghĩa gửi cho mô hình

    /** Thứ tự và nội dung cố định, để mọi request gửi định nghĩa tool giống hệt nhau (prompt cache). */
    static final List<ToolSpec> DEFINITIONS = List.of(
            new ToolSpec(LIST_TODOS,
                    "Liệt kê todo mà người dùng được xem (USER: của mình, ADMIN: tất cả), có phân trang. "
                            + "Dùng khi cần biết người dùng có những việc gì, việc nào xong hoặc chưa xong. "
                            + "Kết quả có totalElements là tổng số todo khớp bộ lọc.",
                    objectSchema(
                            property("completed", nullable("boolean",
                                    "true: chỉ việc đã xong, false: chỉ việc chưa xong, null: tất cả")),
                            property("page", type("integer", "Số trang, bắt đầu từ 0")),
                            property("size", type("integer", "Số todo mỗi trang, từ 1 đến " + MAX_LIST_SIZE)),
                            property("sortBy", enumOf(List.of("createdAt", "updatedAt", "title", "id"),
                                    "Trường sắp xếp")),
                            property("direction", enumOf(List.of("asc", "desc"), "Chiều sắp xếp")))),
            new ToolSpec(GET_TODO,
                    "Xem chi tiết một todo theo id. Trả lỗi nếu không tìm thấy.",
                    objectSchema(property("id", type("integer", "Id của todo")))),
            new ToolSpec(SUGGEST_TODO,
                    "Gợi ý MỘT todo mới để người dùng tự điền vào form tạo. KHÔNG tạo todo trong hệ thống. "
                            + "Gọi một lần cho mỗi todo muốn gợi ý.",
                    objectSchema(
                            property("title", type("string",
                                    "Tiêu đề ngắn gọn, tối đa " + Todo.MAX_TITLE_LENGTH + " ký tự")),
                            property("description", nullable("string",
                                    "Mô tả chi tiết, tối đa " + Todo.MAX_DESCRIPTION_LENGTH
                                            + " ký tự, hoặc null nếu không cần")))));

    /**
     * Schema kiểu object, mọi thuộc tính đều bắt buộc và không cho thuộc tính lạ (yêu cầu của strict tool use).
     * Thuộc tính tùy chọn được khai báo là "có thể null".
     */
    @SafeVarargs
    private static Map<String, Object> objectSchema(Map.Entry<String, Object>... properties) {
        Map<String, Object> props = new LinkedHashMap<>();
        for (Map.Entry<String, Object> property : properties) {
            props.put(property.getKey(), property.getValue());
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.copyOf(props.keySet()));
        schema.put("additionalProperties", false);
        return schema;
    }

    private static Map.Entry<String, Object> property(String name, Map<String, Object> schema) {
        return Map.entry(name, schema);
    }

    private static Map<String, Object> type(String type, String description) {
        return Map.of("type", type, "description", description);
    }

    private static Map<String, Object> enumOf(List<String> values, String description) {
        return Map.of("type", "string", "enum", values, "description", description);
    }

    private static Map<String, Object> nullable(String type, String description) {
        return Map.of("anyOf", List.of(Map.of("type", type), Map.of("type", "null")), "description", description);
    }
}
