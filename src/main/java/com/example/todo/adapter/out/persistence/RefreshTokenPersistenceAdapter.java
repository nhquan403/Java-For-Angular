package com.example.todo.adapter.out.persistence;

import com.example.todo.application.port.out.RefreshTokenRepositoryPort;
import com.example.todo.domain.model.RefreshToken;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/** OUTBOUND ADAPTER: hiện thực RefreshTokenRepositoryPort bằng Spring Data JPA. */
@Component
class RefreshTokenPersistenceAdapter implements RefreshTokenRepositoryPort {

    private final SpringDataRefreshTokenRepository repository;

    RefreshTokenPersistenceAdapter(SpringDataRefreshTokenRepository repository) {
        this.repository = repository;
    }

    @Override
    public RefreshToken save(RefreshToken token) {
        return toDomain(repository.save(toEntity(token)));
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash).map(this::toDomain);
    }

    @Override
    public boolean revokeIfActive(Long tokenId, Instant now) {
        return repository.revokeIfActive(tokenId, now) == 1;
    }

    @Override
    public int revokeAllActiveForUser(Long userId, Instant now) {
        return repository.revokeAllActive(userId, now);
    }

    @Override
    public int deleteExpiredBefore(Instant cutoff) {
        return repository.deleteExpiredBefore(cutoff);
    }

    private RefreshTokenJpaEntity toEntity(RefreshToken token) {
        return new RefreshTokenJpaEntity(token.id(), token.userId(), token.tokenHash(),
                token.createdAt(), token.expiresAt(), token.revokedAt());
    }

    private RefreshToken toDomain(RefreshTokenJpaEntity entity) {
        return RefreshToken.reconstitute(entity.getId(), entity.getUserId(), entity.getTokenHash(),
                entity.getCreatedAt(), entity.getExpiresAt(), entity.getRevokedAt());
    }
}
