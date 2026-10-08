package com.example.todo.application.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageQueryTest {

    @Test
    void ofParsesValidInput() {
        PageQuery query = PageQuery.of(1, 50, "createdAt", "DESC");

        assertThat(query.page()).isEqualTo(1);
        assertThat(query.size()).isEqualTo(50);
        assertThat(query.sortBy()).isEqualTo(PageQuery.SortField.CREATED_AT);
        assertThat(query.direction()).isEqualTo(PageQuery.Direction.DESC);
    }

    @Test
    void negativePageIsRejected() {
        assertThatThrownBy(() -> PageQuery.of(-1, 10, "id", "asc"))
                .isInstanceOf(InvalidPageQueryException.class);
    }

    @Test
    void sizeOutsideRangeIsRejected() {
        assertThatThrownBy(() -> PageQuery.of(0, 0, "id", "asc"))
                .isInstanceOf(InvalidPageQueryException.class);
        assertThatThrownBy(() -> PageQuery.of(0, PageQuery.MAX_SIZE + 1, "id", "asc"))
                .isInstanceOf(InvalidPageQueryException.class);
    }

    @Test
    void unknownSortFieldIsRejected() {
        assertThatThrownBy(() -> PageQuery.of(0, 10, "password", "asc"))
                .isInstanceOf(InvalidPageQueryException.class);
    }

    @Test
    void unknownDirectionIsRejected() {
        assertThatThrownBy(() -> PageQuery.of(0, 10, "id", "sideways"))
                .isInstanceOf(InvalidPageQueryException.class);
    }
}
