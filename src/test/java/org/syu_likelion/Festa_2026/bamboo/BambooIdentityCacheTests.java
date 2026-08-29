package org.syu_likelion.Festa_2026.bamboo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BambooIdentityCacheTests {
    private static final UUID USER = UUID.fromString("123e4567-e89b-12d3-a456-4266141740d0");

    private MutableClock clock;
    private BambooIdentityCache cache;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-06T10:00:00Z"));
        cache = new BambooIdentityCache(properties(20_000), clock);
    }

    @Test
    void returnsTheStoredUserUntilTheEntryExpires() {
        cache.store("access-token", USER);

        assertThat(cache.find("access-token")).isEqualTo(USER);

        clock.advance(Duration.ofSeconds(59));
        assertThat(cache.find("access-token")).isEqualTo(USER);

        clock.advance(Duration.ofSeconds(1));
        assertThat(cache.find("access-token")).isNull();
    }

    @Test
    void doesNotConfuseDifferentTokens() {
        cache.store("token-a", USER);

        assertThat(cache.find("token-b")).isNull();
        assertThat(cache.find(null)).isNull();
    }

    @Test
    void dropsExpiredEntriesWhenTheCapacityIsReached() {
        BambooIdentityCache small = new BambooIdentityCache(properties(2), clock);
        small.store("token-1", USER);
        small.store("token-2", USER);
        assertThat(small.size()).isEqualTo(2);

        clock.advance(Duration.ofSeconds(61));
        small.store("token-3", USER);

        assertThat(small.find("token-1")).isNull();
        assertThat(small.find("token-3")).isEqualTo(USER);
    }

    @Test
    void staysWithinCapacityWhenNothingHasExpired() {
        BambooIdentityCache small = new BambooIdentityCache(properties(2), clock);
        small.store("token-1", USER);
        small.store("token-2", USER);
        small.store("token-3", USER);

        assertThat(small.size()).isEqualTo(2);
    }

    private BambooProperties properties(int maxEntries) {
        return new BambooProperties(100, Duration.ofSeconds(5), 10, 200, 10, 100_000,
                Duration.ofSeconds(60), maxEntries, List.of());
    }
}
