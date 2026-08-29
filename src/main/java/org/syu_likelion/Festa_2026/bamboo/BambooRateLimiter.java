package org.syu_likelion.Festa_2026.bamboo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.syu_likelion.Festa_2026.error.ApiException;

/**
 * 사용자별 작성·신고 빈도 제한과 직전 메시지 중복 차단.
 *
 * <p>직전 메시지 해시를 함께 들고 있으므로 중복 검사에 DB 조회가 필요 없다.
 * 축제 당일 24시간 연속 운영이므로 맵에 최대 크기와 만료 제거를 함께 건다.
 */
@Component
public class BambooRateLimiter {
    // ponytail: 고정 윈도우라 경계에서 최대 두 배까지 통과할 수 있다.
    //           도배 억제에는 충분하다. 정확한 슬라이딩 윈도우가 필요해지면 교체.
    private static final Duration MINUTE = Duration.ofMinutes(1);
    private static final Duration HOUR = Duration.ofHours(1);

    private final ConcurrentHashMap<UUID, Usage> writes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Window> reports = new ConcurrentHashMap<>();
    private final BambooProperties properties;
    private final Clock clock;

    public BambooRateLimiter(BambooProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 작성 허용 여부를 판정하고 통과하면 사용량을 기록한다.
     * {@link ConcurrentHashMap#compute}는 함수가 예외를 던지면 매핑을 남기지 않으므로,
     * 거부된 시도는 한도를 소모하지 않는다.
     */
    public void checkWrite(UUID userUuid, String content) {
        Instant now = Instant.now(clock);
        String contentHash = hash(content);
        guardCapacity(writes, now, entry -> entry.getValue().lastWriteAt());
        writes.compute(userUuid, (ignored, current) -> {
            if (current != null) {
                // 간격이 0이면 최소 간격 검사를 하지 않는다. now 를 임계 구역 밖에서 읽기 때문에
                // 스레드 간 미세한 시계 역전이 생길 수 있고, 간격이 0일 때 그것이 오탐으로 드러난다.
                if (!properties.writeInterval().isZero()
                        && now.isBefore(current.lastWriteAt().plus(properties.writeInterval()))) {
                    throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "BAMBOO_TOO_FAST",
                            "메시지를 너무 빠르게 보내고 있습니다. 잠시 후 다시 시도해 주세요.");
                }
                if (contentHash.equals(current.lastContentHash())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "BAMBOO_DUPLICATE_MESSAGE",
                            "직전에 보낸 메시지와 같습니다.");
                }
                if (within(current.minuteStart(), now, MINUTE)
                        && current.minuteCount() >= properties.writesPerMinute()) {
                    throw rateLimited();
                }
                if (within(current.hourStart(), now, HOUR)
                        && current.hourCount() >= properties.writesPerHour()) {
                    throw rateLimited();
                }
            }
            return advance(current, now, contentHash);
        });
    }

    public void checkReport(UUID userUuid) {
        Instant now = Instant.now(clock);
        guardCapacity(reports, now, entry -> entry.getValue().startedAt());
        reports.compute(userUuid, (ignored, current) -> {
            if (current != null && within(current.startedAt(), now, MINUTE)) {
                if (current.count() >= properties.reportsPerMinute()) throw rateLimited();
                return new Window(current.startedAt(), current.count() + 1);
            }
            return new Window(now, 1);
        });
    }

    private Usage advance(Usage current, Instant now, String contentHash) {
        if (current == null) return new Usage(now, now, 1, now, 1, contentHash);
        boolean sameMinute = within(current.minuteStart(), now, MINUTE);
        boolean sameHour = within(current.hourStart(), now, HOUR);
        return new Usage(now,
                sameMinute ? current.minuteStart() : now,
                sameMinute ? current.minuteCount() + 1 : 1,
                sameHour ? current.hourStart() : now,
                sameHour ? current.hourCount() + 1 : 1,
                contentHash);
    }

    private boolean within(Instant start, Instant now, Duration window) {
        return now.isBefore(start.plus(window));
    }

    /**
     * 만료 항목을 먼저 비우고, 그래도 가득 차 있으면 가장 오래된 항목부터 자리를 만든다.
     * 정리만 하고 넘어가면 상한을 넘겨 계속 증가한다.
     */
    private <T> void guardCapacity(ConcurrentHashMap<UUID, T> map, Instant now,
                                   java.util.function.Function<java.util.Map.Entry<UUID, T>, Instant> lastSeen) {
        int limit = properties.maxTrackedUsers();
        if (map.size() < limit) return;
        Instant cutoff = now.minus(HOUR);
        map.entrySet().removeIf(entry -> lastSeen.apply(entry).isBefore(cutoff));
        int excess = map.size() - limit + 1;
        if (excess <= 0) return;
        map.entrySet().stream()
                .sorted(java.util.Comparator.comparing(lastSeen))
                .limit(excess)
                .map(java.util.Map.Entry::getKey)
                .toList()
                .forEach(map::remove);
    }

    /** 테스트에서 싱글턴 상태를 초기화하기 위한 통로. */
    void clear() {
        writes.clear();
        reports.clear();
    }

    @Scheduled(fixedDelay = 300_000)
    void scheduledCleanup() {
        Instant cutoff = Instant.now(clock).minus(HOUR);
        writes.entrySet().removeIf(entry -> entry.getValue().lastWriteAt().isBefore(cutoff));
        reports.entrySet().removeIf(entry -> entry.getValue().startedAt().isBefore(cutoff));
    }

    private ApiException rateLimited() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "BAMBOO_RATE_LIMIT",
                "메시지를 너무 많이 보냈습니다. 잠시 후 다시 시도해 주세요.");
    }

    private String hash(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private record Usage(Instant lastWriteAt, Instant minuteStart, int minuteCount,
                         Instant hourStart, int hourCount, String lastContentHash) { }

    private record Window(Instant startedAt, int count) { }
}
