package com.example.todo.adapter.in.web.dto;

import com.example.todo.domain.model.Todo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param version phiên bản client đang giữ (lấy từ lần GET trước). Không bắt buộc,
 *                nhưng nên gửi để biết nếu người khác đã sửa trước (khi đó trả về 409).
 */
public record UpdateTodoRequest(
        @NotBlank(message = "title must not be blank")
        @Size(max = Todo.MAX_TITLE_LENGTH, message = "title must not exceed {max} characters")
        String title,

        @Size(max = Todo.MAX_DESCRIPTION_LENGTH, message = "description must not exceed {max} characters")
        String description,

        Long version) {
}
