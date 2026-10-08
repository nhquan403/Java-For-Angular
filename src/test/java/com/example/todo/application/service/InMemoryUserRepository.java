package com.example.todo.application.service;

import com.example.todo.application.common.PageResult;
import com.example.todo.application.port.out.UserRepositoryPort;
import com.example.todo.domain.exception.EmailAlreadyUsedException;
import com.example.todo.domain.model.User;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Fake kho user: mô phỏng ràng buộc email duy nhất của database. */
public class InMemoryUserRepository implements UserRepositoryPort {

    private final Map<Long, User> store = new TreeMap<>();
    private long sequence = 0;

    @Override
    public User save(User user) {
        boolean emailTaken = store.values().stream()
                .anyMatch(u -> u.email().equals(user.email()) && !u.id().equals(user.id()));
        if (emailTaken) {
            throw new EmailAlreadyUsedException();
        }
        Long id = user.id() != null ? user.id() : ++sequence;
        User saved = User.reconstitute(id, user.email(), user.passwordHash(), user.role(), user.createdAt());
        store.put(id, saved);
        return saved;
    }

    @Override
    public Optional<User> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return store.values().stream().filter(u -> u.email().equals(email)).findFirst();
    }

    @Override
    public boolean existsByEmail(String email) {
        return findByEmail(email).isPresent();
    }

    @Override
    public PageResult<User> findAll(int page, int size) {
        List<User> all = List.copyOf(store.values());
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        return PageResult.of(all.subList(from, to), page, size, all.size());
    }
}
