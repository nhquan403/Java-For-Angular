package com.example.todo.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param version phiên bản client đang giữ (lấy từ lần GET trước). Không bắt buộc,
 *                nhưng nên gửi để biết nếu người khác đã sửa trước (khi đó trả về 409).
 */
public record UpdateTodoRequest(
        @NotBlank(message = "title must not be blank")
        @Size(max = 100, message = "title must not exceed 100 characters")
        String title,

        @Size(max = 500, message = "description must not exceed 500 characters")
        String description,

        Long version) {
}
