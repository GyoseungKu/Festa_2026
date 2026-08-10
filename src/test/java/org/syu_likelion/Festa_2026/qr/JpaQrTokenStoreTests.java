package org.syu_likelion.Festa_2026.qr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class JpaQrTokenStoreTests {
    private static final Instant NOW = Instant.parse("2026-07-30T12:00:00Z");
    private FestivalQrTokenRepository repository;
    private JpaQrTokenStore store;

    @BeforeEach
    void setUp() {
        repository = mock(FestivalQrTokenRepository.class);
        store = new JpaQrTokenStore(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void storesOnlyTokenHashUuidAndExpiration() {
        UUID userUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        String rawToken = "raw-secret-qr-token";

        store.save(rawToken, userUuid, Duration.ofSeconds(60));

        ArgumentCaptor<FestivalQrToken> saved = ArgumentCaptor.forClass(FestivalQrToken.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getTokenHash()).hasSize(64).doesNotContain(rawToken);
        assertThat(saved.getValue().getUserUuid()).isEqualTo(userUuid);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void validTokenReturnsUserUuid() {
        UUID userUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        FestivalQrToken token = new FestivalQrToken("hash", userUuid, NOW.plusSeconds(1), NOW);
        when(repository.findById(anyHash())).thenReturn(Optional.of(token));

        assertThat(store.findUserUuid("raw-token")).contains(userUuid);
    }

    @Test
    void expiredTokenIsDeletedAndRejected() {
        UUID userUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        FestivalQrToken token = new FestivalQrToken("hash", userUuid, NOW, NOW.minusSeconds(60));
        when(repository.findById(anyHash())).thenReturn(Optional.of(token));

        assertThat(store.findUserUuid("raw-token")).isEmpty();
        verify(repository).delete(token);
    }

    @Test
    void scheduledCleanupDeletesAllExpiredRows() {
        store.deleteExpiredTokens();
        verify(repository).deleteByExpiresAtLessThanEqual(NOW);
    }

    private String anyHash() {
        return org.mockito.ArgumentMatchers.argThat(value -> value != null && value.length() == 64);
    }
}
