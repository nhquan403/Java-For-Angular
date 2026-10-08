package com.example.todo.adapter.in.realtime;

/** Đường dẫn thời gian thực, gom một chỗ để controller, WebSocket config và bộ lọc ticket dùng chung. */
public final class RealtimeEndpoints {

    public static final String BASE = "/api/realtime";
    public static final String TICKET = BASE + "/ticket";
    public static final String STREAM = BASE + "/stream";
    public static final String WEBSOCKET = "/ws/todos";
    public static final String TICKET_PARAM = "ticket";

    private RealtimeEndpoints() {
    }
}
