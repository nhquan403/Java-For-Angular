package com.example.todo.application.service;

import com.example.todo.application.port.out.RefreshTokenRepositoryPort;
import com.example.todo.domain.model.RefreshToken;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Fake kho refresh token. */
public class InMemoryRefreshTokenRepository implements RefreshTokenRepositoryPort {

    private final Map<Long, RefreshToken> store = new TreeMap<>();
    private long sequence = 0;

    @Override
    public RefreshToken save(RefreshToken token) {
        Long id = token.id() != null ? token.id() : ++sequence;
        RefreshToken saved = RefreshToken.reconstitute(id, token.userId(), token.tokenHash(),
                token.createdAt(), token.expiresAt(), token.revokedAt());
        store.put(id, saved);
        return saved;
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return store.values().stream().filter(t -> t.tokenHash().equals(tokenHash)).findFirst();
    }

    @Override
    public boolean revokeIfActive(Long tokenId, Instant now) {
        RefreshToken token = store.get(tokenId);
        if (token == null || token.isRevoked()) {
            return false;
        }
        store.put(tokenId, withRevokedAt(token, now));
        return true;
    }

    @Override
    public int revokeAllActiveForUser(Long userId, Instant now) {
        int count = 0;
        for (RefreshToken token : java.util.List.copyOf(store.values())) {
            if (token.userId().equals(userId) && !token.isRevoked()) {
                store.put(token.id(), withRevokedAt(token, now));
                count++;
            }
        }
        return count;
    }

    @Override
    public int deleteExpiredBefore(Instant cutoff) {
        int before = store.size();
        store.values().removeIf(t -> t.expiresAt().isBefore(cutoff));
        return before - store.size();
    }

    public long activeCount(Long userId) {
        return store.values().stream().filter(t -> t.userId().equals(userId) && !t.isRevoked()).count();
    }

    public int size() {
        return store.size();
    }

    private static RefreshToken withRevokedAt(RefreshToken t, Instant at) {
        return RefreshToken.reconstitute(t.id(), t.userId(), t.tokenHash(), t.createdAt(), t.expiresAt(), at);
    }
}
