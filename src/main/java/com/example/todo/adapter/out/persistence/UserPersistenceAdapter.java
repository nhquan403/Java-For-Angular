package com.example.todo.adapter.out.persistence;

import com.example.todo.application.common.PageResult;
import com.example.todo.application.port.out.UserRepositoryPort;
import com.example.todo.domain.exception.EmailAlreadyUsedException;
import com.example.todo.domain.model.User;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** OUTBOUND ADAPTER: hiện thực UserRepositoryPort bằng Spring Data JPA. */
@Component
class UserPersistenceAdapter implements UserRepositoryPort {

    private final SpringDataUserRepository repository;

    UserPersistenceAdapter(SpringDataUserRepository repository) {
        this.repository = repository;
    }

    @Override
    public User save(User user) {
        try {
            // saveAndFlush để vi phạm ràng buộc duy nhất (email trùng) nổ ngay trong try này.
            // Đây là lớp bảo vệ cuối cùng khi hai người đăng ký cùng email cùng lúc,
            // vì kiểm tra existsByEmail trước đó không chặn được race condition.
            return toDomain(repository.saveAndFlush(toEntity(user)));
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyUsedException();
        }
    }

    @Override
    public Optional<User> findById(Long id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return repository.findByEmail(email).map(this::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return repository.existsByEmail(email);
    }

    @Override
    public PageResult<User> findAll(int page, int size) {
        Page<UserJpaEntity> result = repository.findAll(PageRequest.of(page, size, Sort.by("id")));
        return PageResult.of(
                result.getContent().stream().map(this::toDomain).toList(),
                page, size, result.getTotalElements());
    }

    private UserJpaEntity toEntity(User user) {
        return new UserJpaEntity(user.id(), user.email(), user.passwordHash(), user.role(), user.createdAt());
    }

    private User toDomain(UserJpaEntity entity) {
        return User.reconstitute(entity.getId(), entity.getEmail(), entity.getPasswordHash(),
                entity.getRole(), entity.getCreatedAt());
    }
}
