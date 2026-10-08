package com.example.todo.adapter.out.messaging;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

interface SpringDataOutboxRepository extends JpaRepository<OutboxEventJpaEntity, Long> {

    /**
     * Khóa tư vấn (advisory lock) của PostgreSQL, tự nhả khi giao dịch kết thúc. Chỉ một instance được giữ
     * khóa tại một thời điểm nên chỉ một instance đẩy outbox, thứ tự sự kiện được giữ nguyên.
     * Hàm này không có trên H2: chế độ Kafka yêu cầu PostgreSQL.
     */
    @Query(value = "SELECT pg_try_advisory_xact_lock(:key)", nativeQuery = true)
    boolean tryAdvisoryXactLock(@Param("key") long key);

    List<OutboxEventJpaEntity> findByPublishedAtIsNullOrderByIdAsc(Pageable pageable);

    @Modifying
    @Transactional
    @Query("delete from OutboxEventJpaEntity e where e.publishedAt < :cutoff")
    int deletePublishedBefore(@Param("cutoff") Instant cutoff);
}
