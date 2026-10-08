package com.example.todo.application.common;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/**
 * Yêu cầu phân trang theo ngôn ngữ của ứng dụng (không dùng Pageable của Spring
 * để lõi không phụ thuộc framework).
 *
 * @param page số trang, bắt đầu từ 0
 * @param size số phần tử mỗi trang, 1..100
 */
public record PageQuery(int page, int size, SortField sortBy, Direction direction) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    /** Các trường được phép sắp xếp (danh sách trắng, tránh client sort theo cột bất kỳ). */
    public enum SortField {
        ID("id"),
        TITLE("title"),
        CREATED_AT("createdAt"),
        UPDATED_AT("updatedAt");

        private final String apiName;

        SortField(String apiName) {
            this.apiName = apiName;
        }

        public String apiName() {
            return apiName;
        }

        static SortField fromApiName(String value) {
            return Arrays.stream(values())
                    .filter(f -> f.apiName.equals(value))
                    .findFirst()
                    .orElseThrow(() -> new InvalidPageQueryException(
                            "sortBy must be one of: id, title, createdAt, updatedAt"));
        }
    }

    public enum Direction {
        ASC, DESC;

        static Direction fromApiName(String value) {
            try {
                return valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new InvalidPageQueryException("direction must be asc or desc");
            }
        }
    }

    public PageQuery {
        Objects.requireNonNull(sortBy, "sortBy must not be null");
        Objects.requireNonNull(direction, "direction must not be null");
        if (page < 0) {
            throw new InvalidPageQueryException("page must be >= 0");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new InvalidPageQueryException("size must be between 1 and " + MAX_SIZE);
        }
    }

    /** Dựng từ tham số thô của client (chuỗi), kiểm tra hợp lệ tại đây. */
    public static PageQuery of(int page, int size, String sortBy, String direction) {
        return new PageQuery(page, size, SortField.fromApiName(sortBy), Direction.fromApiName(direction));
    }

    /** Trang đầu, mới nhất trước. */
    public static PageQuery defaults() {
        return new PageQuery(0, DEFAULT_SIZE, SortField.CREATED_AT, Direction.DESC);
    }
}
