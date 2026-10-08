package com.example.todo.adapter.in.web.dto;

import com.example.todo.application.common.Actor;
import com.example.todo.application.port.in.ChatWithAssistantUseCase;
import com.example.todo.application.port.in.ChatWithAssistantUseCase.ChatMessage;
import com.example.todo.application.port.in.ChatWithAssistantUseCase.Role;

import java.util.List;

/**
 * Body của POST /api/assistant/chat. Hợp đồng cố định với frontend, không đổi tên trường.
 * Kiểm tra hợp lệ nằm ở lõi (ChatWithAssistantUseCase.Command), không tin dữ liệu từ client.
 */
public record AssistantChatRequest(List<Message> messages, Context context) {

    public record Message(String role, String content) {
    }

    public record Context(String page, String path, Query query, Long todoId, Draft createDraft) {
    }

    public record Query(Boolean completed, Integer page, Integer size, String sortBy, String direction) {
    }

    public record Draft(String title, String description) {
    }

    public ChatWithAssistantUseCase.Command toCommand(Actor actor) {
        List<ChatMessage> history = messages == null ? null : messages.stream()
                .map(m -> m == null ? null : new ChatMessage(Role.fromApiName(m.role()), m.content()))
                .toList();
        return new ChatWithAssistantUseCase.Command(actor, history, context == null ? null : toPageContext(context));
    }

    private static ChatWithAssistantUseCase.PageContext toPageContext(Context c) {
        Query q = c.query();
        Draft d = c.createDraft();
        return new ChatWithAssistantUseCase.PageContext(
                c.page(),
                c.path(),
                q == null ? null : new ChatWithAssistantUseCase.ListQuery(
                        q.completed(), q.page(), q.size(), q.sortBy(), q.direction()),
                c.todoId(),
                d == null ? null : new ChatWithAssistantUseCase.Draft(d.title(), d.description()));
    }

    /** Không in nội dung hội thoại ra log. */
    @Override
    public String toString() {
        return "AssistantChatRequest[messages=" + (messages == null ? 0 : messages.size()) + "]";
    }
}
