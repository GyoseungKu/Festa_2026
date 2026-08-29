package org.syu_likelion.Festa_2026.bamboo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Access Token 에서 사용자 식별자로의 단기 캐시.
 *
 * <p>{@code UserService.getMe()}는 요청 하나 안에서만 캐시되므로 인증된 요청마다 SSO 를 호출한다.
 * 채팅은 요청 빈도가 다른 기능과 자릿수가 달라, 캐시가 없으면 SSO 가 먼저 포화되고
 * SSO 응답 타임아웃(5초) 동안 Tomcat 스레드가 함께 고갈된다.
 *
 * <p>토큰 원문은 보관하지 않고 SHA-256 해시만 키로 쓴다. 사용자 식별자만 담으므로
 * 권한 변경이나 작성 차단(mute)은 이 캐시의 영향을 받지 않는다.
 */
@Component
public class BambooIdentityCache {
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final BambooProperties properties;
    private final Clock clock;

    public BambooIdentityCache(BambooProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public UUID find(String accessToken) {
        if (accessToken == null) return null;
        Entry entry = entries.get(hash(accessToken));
        if (entry == null) return null;
        if (!Instant.now(clock).isBefore(entry.expiresAt())) return null;
        return entry.userUuid();
    }

    public void store(String accessToken, UUID userUuid) {
        if (accessToken == null || userUuid == null) return;
        Instant now = Instant.now(clock);
        if (entries.size() >= properties.identityMaxEntries()) cleanup(now);
        if (entries.size() >= properties.identityMaxEntries()) return;
        entries.put(hash(accessToken), new Entry(userUuid, now.plus(properties.identityTtl())));
    }

    @Scheduled(fixedDelay = 60_000)
    void scheduledCleanup() { cleanup(Instant.now(clock)); }

    private void cleanup(Instant now) {
        entries.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
    }

    int size() { return entries.size(); }

    private String hash(String accessToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(accessToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private record Entry(UUID userUuid, Instant expiresAt) { }
}
