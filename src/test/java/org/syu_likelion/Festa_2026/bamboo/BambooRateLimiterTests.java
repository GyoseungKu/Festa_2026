package org.syu_likelion.Festa_2026.bamboo;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.syu_likelion.Festa_2026.error.ApiException;

class BambooRateLimiterTests {
    private static final UUID USER = UUID.fromString("123e4567-e89b-12d3-a456-4266141740c0");
    private static final UUID OTHER = UUID.fromString("123e4567-e89b-12d3-a456-4266141740c1");

    private MutableClock clock;
    private BambooRateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-06T10:00:00Z"));
        limiter = new BambooRateLimiter(new BambooProperties(100, Duration.ofSeconds(5), 10, 200, 10,
                100_000, List.of()), clock);
    }

    @Test
    void rejectsASecondMessageInsideTheInterval() {
        limiter.checkWrite(USER, "첫 메시지");

        assertCode(() -> limiter.checkWrite(USER, "둘째 메시지"), "BAMBOO_TOO_FAST");

        clock.advance(Duration.ofSeconds(5));
        assertThatCode(() -> limiter.checkWrite(USER, "둘째 메시지")).doesNotThrowAnyException();
    }

    @Test
    void rejectsTheSameContentTwiceInARow() {
        limiter.checkWrite(USER, "같은 말");
        clock.advance(Duration.ofSeconds(10));

        assertCode(() -> limiter.checkWrite(USER, "같은 말"), "BAMBOO_DUPLICATE_MESSAGE");

        assertThatCode(() -> limiter.checkWrite(USER, "다른 말")).doesNotThrowAnyException();
        clock.advance(Duration.ofSeconds(10));
        assertThatCode(() -> limiter.checkWrite(USER, "같은 말")).doesNotThrowAnyException();
    }

    @Test
    void enforcesThePerMinuteLimit() {
        for (int index = 0; index < 10; index++) {
            limiter.checkWrite(USER, "메시지 " + index);
            clock.advance(Duration.ofSeconds(5));
        }

        assertCode(() -> limiter.checkWrite(USER, "열한번째"), "BAMBOO_RATE_LIMIT");
    }

    @Test
    void startsAFreshWindowAfterTheMinutePasses() {
        for (int index = 0; index < 10; index++) {
            limiter.checkWrite(USER, "메시지 " + index);
            clock.advance(Duration.ofSeconds(5));
        }
        assertCode(() -> limiter.checkWrite(USER, "열한번째"), "BAMBOO_RATE_LIMIT");

        clock.advance(Duration.ofMinutes(1));
        assertThatCode(() -> limiter.checkWrite(USER, "다음 창의 첫 메시지")).doesNotThrowAnyException();
    }

    @Test
    void rejectedAttemptsDoNotConsumeTheAllowance() {
        limiter.checkWrite(USER, "첫 메시지");
        for (int attempt = 0; attempt < 20; attempt++) {
            assertCode(() -> limiter.checkWrite(USER, "너무 빠름"), "BAMBOO_TOO_FAST");
        }

        clock.advance(Duration.ofSeconds(5));
        for (int index = 1; index < 10; index++) {
            limiter.checkWrite(USER, "메시지 " + index);
            clock.advance(Duration.ofSeconds(5));
        }
        assertCode(() -> limiter.checkWrite(USER, "열한번째"), "BAMBOO_RATE_LIMIT");
    }

    @Test
    void tracksUsersIndependently() {
        limiter.checkWrite(USER, "내 메시지");

        assertThatCode(() -> limiter.checkWrite(OTHER, "내 메시지")).doesNotThrowAnyException();
    }

    @Test
    void limitsReportsPerMinute() {
        for (int index = 0; index < 10; index++) limiter.checkReport(USER);

        assertCode(() -> limiter.checkReport(USER), "BAMBOO_RATE_LIMIT");

        clock.advance(Duration.ofMinutes(1));
        assertThatCode(() -> limiter.checkReport(USER)).doesNotThrowAnyException();
    }

    private void assertCode(ThrowingCallable call, String expectedCode) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo(expectedCode);
    }
}
