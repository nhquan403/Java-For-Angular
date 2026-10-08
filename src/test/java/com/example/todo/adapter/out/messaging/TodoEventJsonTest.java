package com.example.todo.adapter.out.messaging;

import com.example.todo.domain.event.TodoEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TodoEventJsonTest {

    private static final Instant T = Instant.parse("2026-10-06T10:00:00Z");

    private static String json(String title) {
        return TodoEventJson.toJson(new TodoEvent(TodoEvent.Type.CREATED, 5L, 1L, title, false, T));
    }

    @Test
    void producesTheExpectedShape() {
        assertThat(json("Buy milk")).isEqualTo(
                "{\"type\":\"CREATED\",\"todoId\":5,\"ownerId\":1,\"title\":\"Buy milk\","
                        + "\"completed\":false,\"occurredAt\":\"2026-10-06T10:00:00Z\"}");
    }

    @Test
    void nullOwnerAndNullTitleBecomeJsonNull() {
        String s = TodoEventJson.toJson(new TodoEvent(TodoEvent.Type.DELETED, 5L, null, null, true, T));

        assertThat(s).contains("\"ownerId\":null").contains("\"title\":null").contains("\"completed\":true");
    }

    @Test
    void quotesAndBackslashesAreEscaped() {
        assertThat(json("say \"hi\" \\ bye")).contains("\"title\":\"say \\\"hi\\\" \\\\ bye\"");
    }

    @Test
    void controlCharactersAreEscapedSoTheJsonStaysOnOneLine() {
        String s = json("line1\nline2\ttab\r\u0001");

        assertThat(s).doesNotContain("\n").doesNotContain("\r").doesNotContain("\t");
        assertThat(s).contains("line1\\nline2\\ttab\\r\\u0001");
    }

    @Test
    void lineSeparatorsAreEscaped() {
        assertThat(json("a b c")).contains("a\\u2028b\\u2029c");
    }

    @Test
    void unicodeTextIsKeptAsIs() {
        assertThat(json("Học Spring Boot")).contains("\"title\":\"Học Spring Boot\"");
    }

    @Test
    void anAttackerCannotInjectExtraFieldsThroughTheTitle() {
        String s = json("x\",\"ownerId\":999,\"y\":\"");

        // dấu ngoặc kép trong title đã bị thoát nên không thể đóng chuỗi sớm
        assertThat(s).contains("\\\",\\\"ownerId\\\":999");
        assertThat(s).contains("\"ownerId\":1,");
    }
}
