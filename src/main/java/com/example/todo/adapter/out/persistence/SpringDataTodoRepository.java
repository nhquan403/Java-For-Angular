package com.example.todo.adapter.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data tự sinh phần cài đặt từ tên method.
 * findAll(Pageable) có sẵn trong JpaRepository.
 */
public interface SpringDataTodoRepository extends JpaRepository<TodoJpaEntity, Long> {

    Page<TodoJpaEntity> findByCompleted(boolean completed, Pageable pageable);

    Page<TodoJpaEntity> findByOwnerId(Long ownerId, Pageable pageable);

    Page<TodoJpaEntity> findByOwnerIdAndCompleted(Long ownerId, boolean completed, Pageable pageable);
}
