package org.syu_likelion.Festa_2026.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.syu_likelion.Festa_2026.error.ApiException;

@Component
public class AccountRecoveryAttemptLimiter {
    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_TRACKED_KEYS = 100_000;
    private static final Duration WINDOW = Duration.ofMinutes(10);
    private final ConcurrentHashMap<String, Window> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public AccountRecoveryAttemptLimiter(Clock clock) { this.clock = clock; }

    public void check(String email) {
        Instant now = Instant.now(clock);
        String key = hash(email);
        if (!attempts.containsKey(key) && attempts.size() >= MAX_TRACKED_KEYS) cleanup(now);
        if (!attempts.containsKey(key) && attempts.size() >= MAX_TRACKED_KEYS) {
            throw tooManyAttempts();
        }
        Window updated = attempts.compute(key, (ignored, current) -> {
            if (current == null || !now.isBefore(current.startedAt().plus(WINDOW))) {
                return new Window(now, 1);
            }
            return new Window(current.startedAt(), current.count() + 1);
        });
        if (updated.count() > MAX_ATTEMPTS) throw tooManyAttempts();
    }

    public void clear(String email) { attempts.remove(hash(email)); }

    @Scheduled(fixedDelay = 60_000)
    void scheduledCleanup() { cleanup(Instant.now(clock)); }

    private void cleanup(Instant now) {
        attempts.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().startedAt().plus(WINDOW)));
    }

    private String hash(String email) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(email.trim().toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private ApiException tooManyAttempts() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RECOVERY_ATTEMPTS_EXCEEDED",
                "인증 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.");
    }

    private record Window(Instant startedAt, int count) { }
}
