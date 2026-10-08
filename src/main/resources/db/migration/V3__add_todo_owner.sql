-- Mỗi todo thuộc về một user.
-- Cột cho phép NULL vì todo tạo trước khi có đăng nhập chưa có chủ. Todo như vậy chỉ ADMIN thấy.
-- Todo mới tạo từ ứng dụng luôn có owner_id.

ALTER TABLE todos ADD COLUMN owner_id BIGINT;

ALTER TABLE todos
    ADD CONSTRAINT fk_todos_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE;

CREATE INDEX idx_todos_owner_id ON todos (owner_id);
