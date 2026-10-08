package com.example.todo.application.port.out;

import com.example.todo.application.common.PageResult;
import com.example.todo.domain.model.User;

import java.util.Optional;

/** OUTBOUND PORT: kho lưu người dùng. */
public interface UserRepositoryPort {

    /**
     * @throws com.example.todo.domain.exception.EmailAlreadyUsedException nếu email đã tồn tại
     *                                                                     (ràng buộc duy nhất của database)
     */
    User save(User user);

    Optional<User> findById(Long id);

    /** Email đã được chuẩn hóa (chữ thường). */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Danh sách theo id tăng dần. Trang đầu là page = 0. */
    PageResult<User> findAll(int page, int size);
}
