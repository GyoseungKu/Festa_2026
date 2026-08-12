package org.syu_likelion.Festa_2026.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.syu_likelion.Festa_2026.error.ApiException;

class AccountRecoveryAttemptLimiterTests {
    @Test
    void limitsRepeatedResetVerificationAndCanBeClearedAfterSuccess() {
        AccountRecoveryAttemptLimiter limiter = new AccountRecoveryAttemptLimiter(
                new FixedClock(Instant.parse("2026-08-10T00:00:00Z")));
        for (int attempt = 0; attempt < 5; attempt++) limiter.check("student@example.com");
        assertThatThrownBy(() -> limiter.check("student@example.com"))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(
                        ((ApiException) error).status().value()).isEqualTo(429));

        limiter.clear("student@example.com");
        assertThatCode(() -> limiter.check("student@example.com")).doesNotThrowAnyException();
    }

    private static final class FixedClock extends Clock {
        private final Instant instant;
        private FixedClock(Instant instant) { this.instant = instant; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
