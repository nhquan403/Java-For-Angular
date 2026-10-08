package com.example.todo.application.port.in;

import com.example.todo.application.common.Actor;
import com.example.todo.application.common.AssistantEvent;
import com.example.todo.application.common.InvalidAssistantRequestException;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * INBOUND PORT: hỏi trợ lý AI. Chạy theo hai bước để adapter web trả đúng HTTP status:
 * <ol>
 *   <li>{@link #start} chạy trên luồng của request: kiểm tra yêu cầu, giới hạn tần suất, gọi mô hình vòng đầu.
 *       Lỗi ở bước này thành HTTP 400 / 429 / 503.</li>
 *   <li>{@link Reply#deliver} chạy trên luồng khác: phát sự kiện cho client, chạy tool, gọi mô hình các vòng sau.
 *       Lỗi ở bước này thành sự kiện error.</li>
 * </ol>
 * Trợ lý chỉ ĐỌC todo với quyền của actor, không tạo, sửa hay xóa gì.
 */
public interface ChatWithAssistantUseCase {

    /**
     * @throws InvalidAssistantRequestException                                        yêu cầu không hợp lệ
     * @throws com.example.todo.application.common.TooManyAssistantRequestsException hỏi quá nhiều trong một phút
     * @throws com.example.todo.application.common.AssistantModelException          mô hình lỗi ngay vòng đầu
     */
    Reply start(Command command);

    /** Câu trả lời đã sẵn sàng phát. Chỉ gọi deliver một lần. */
    interface Reply {
        Outcome deliver(AssistantEventSink sink);
    }

    /** Nơi nhận sự kiện (adapter web ghi ra SSE). */
    interface AssistantEventSink {
        /** @return false nếu client đã ngắt kết nối: use case dừng ngay, không gọi mô hình thêm. */
        boolean emit(AssistantEvent event);
    }

    /** Tóm tắt một lần trả lời để ghi log. Không chứa nội dung hội thoại. */
    record Outcome(Status status, int toolRounds, long inputTokens, long outputTokens) {
        public enum Status { COMPLETED, REFUSED, TOO_MANY_TOOL_ROUNDS, MODEL_ERROR, CANCELLED }
    }

    /**
     * @param messages lịch sử hội thoại do client gửi, tin cuối là câu hỏi mới
     * @param context  trang người dùng đang xem, null nếu không có
     */
    record Command(Actor actor, List<ChatMessage> messages, PageContext context) {

        public static final int MAX_MESSAGES = 20;
        public static final int MAX_MESSAGE_LENGTH = 4000;

        public Command {
            Objects.requireNonNull(actor, "actor must not be null");
            if (messages == null || messages.isEmpty()) {
                throw new InvalidAssistantRequestException("messages must not be empty");
            }
            if (messages.size() > MAX_MESSAGES) {
                throw new InvalidAssistantRequestException("messages must contain at most " + MAX_MESSAGES + " items");
            }
            // Không dùng contains(null): List.of(...) ném NullPointerException khi hỏi về null.
            if (messages.stream().anyMatch(Objects::isNull)) {
                throw new InvalidAssistantRequestException("messages must not contain null items");
            }
            if (messages.getFirst().role() != Role.USER) {
                throw new InvalidAssistantRequestException("the first message must have role user");
            }
            // Tin cuối là assistant thì mô hình hiểu là "viết tiếp câu trả lời" (prefill), mô hình hiện tại từ chối.
            if (messages.getLast().role() != Role.USER) {
                throw new InvalidAssistantRequestException("the last message must have role user");
            }
            messages = List.copyOf(messages);
        }
    }

    enum Role {
        USER("user"), ASSISTANT("assistant");

        private final String apiName;

        Role(String apiName) {
            this.apiName = apiName;
        }

        public String apiName() {
            return apiName;
        }

        public static Role fromApiName(String value) {
            for (Role role : values()) {
                if (role.apiName.equals(value)) {
                    return role;
                }
            }
            throw new InvalidAssistantRequestException("role must be user or assistant");
        }
    }

    record ChatMessage(Role role, String content) {

        public ChatMessage {
            if (role == null) {
                throw new InvalidAssistantRequestException("role must be user or assistant");
            }
            if (content == null || content.isBlank()) {
                throw new InvalidAssistantRequestException("message content must not be blank");
            }
            if (content.length() > Command.MAX_MESSAGE_LENGTH) {
                throw new InvalidAssistantRequestException(
                        "message content must not exceed " + Command.MAX_MESSAGE_LENGTH + " characters");
            }
        }
    }

    /**
     * Ngữ cảnh trang của frontend, chỉ để trợ lý hiểu "todo này", "danh sách đang lọc", "cái tôi đang gõ".
     * Không dùng để phân quyền: tool luôn đọc dữ liệu với quyền của actor.
     */
    record PageContext(String page, String path, ListQuery query, Long todoId, Draft createDraft) {

        public static final Set<String> PAGES = Set.of("todo-list", "todo-detail", "admin-users", "other");
        public static final int MAX_PATH_LENGTH = 200;

        public PageContext {
            if (page == null || !PAGES.contains(page)) {
                throw new InvalidAssistantRequestException(
                        "context.page must be one of: todo-list, todo-detail, admin-users, other");
            }
            if (path != null && path.length() > MAX_PATH_LENGTH) {
                throw new InvalidAssistantRequestException(
                        "context.path must not exceed " + MAX_PATH_LENGTH + " characters");
            }
            if (todoId != null && todoId < 1) {
                throw new InvalidAssistantRequestException("context.todoId must be positive");
            }
        }
    }

    /** Bộ lọc của danh sách todo đang hiển thị. Kiểm tra giống tham số của GET /api/todos. */
    record ListQuery(Boolean completed, Integer page, Integer size, String sortBy, String direction) {

        private static final Set<String> SORT_FIELDS = Set.of("createdAt", "updatedAt", "title", "id");
        private static final Set<String> DIRECTIONS = Set.of("asc", "desc");

        public ListQuery {
            if (page != null && page < 0) {
                throw new InvalidAssistantRequestException("context.query.page must be >= 0");
            }
            if (size != null && (size < 1 || size > 100)) {
                throw new InvalidAssistantRequestException("context.query.size must be between 1 and 100");
            }
            if (sortBy != null && !SORT_FIELDS.contains(sortBy)) {
                throw new InvalidAssistantRequestException(
                        "context.query.sortBy must be one of: createdAt, updatedAt, title, id");
            }
            if (direction != null && !DIRECTIONS.contains(direction)) {
                throw new InvalidAssistantRequestException("context.query.direction must be asc or desc");
            }
        }
    }

    /**
     * Nội dung người dùng đang gõ trong form tạo todo. Bị cắt bớt (không báo lỗi) khi dài hơn giới hạn của todo,
     * vì đây chỉ là ngữ cảnh, không đáng làm hỏng câu hỏi.
     */
    record Draft(String title, String description) {
    }
}
