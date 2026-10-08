package com.example.todo.adapter.in.web.dto;

import com.example.todo.application.common.PageResult;

import java.util.List;
import java.util.function.Function;

/** Khung JSON trả về cho danh sách phân trang. */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last) {

    public static <S, T> PageResponse<T> from(PageResult<S> result, Function<? super S, ? extends T> mapper) {
        List<T> content = result.content().stream().<T>map(mapper).toList();
        boolean last = result.page() + 1 >= result.totalPages();
        return new PageResponse<>(content, result.page(), result.size(),
                result.totalElements(), result.totalPages(), last);
    }
}
