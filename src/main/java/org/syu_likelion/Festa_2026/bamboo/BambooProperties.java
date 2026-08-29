package org.syu_likelion.Festa_2026.bamboo;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("bamboo")
public record BambooProperties(
        int maxConcurrentRequests,
        Duration writeInterval,
        int writesPerMinute,
        int writesPerHour,
        int reportsPerMinute,
        int maxTrackedUsers,
        Duration identityTtl,
        int identityMaxEntries,
        List<String> blockedWords) {

    public BambooProperties {
        if (maxConcurrentRequests <= 0) maxConcurrentRequests = 100;
        if (writeInterval == null || writeInterval.isNegative()) writeInterval = Duration.ofSeconds(5);
        if (writesPerMinute <= 0) writesPerMinute = 10;
        if (writesPerHour <= 0) writesPerHour = 200;
        if (reportsPerMinute <= 0) reportsPerMinute = 10;
        if (maxTrackedUsers <= 0) maxTrackedUsers = 100_000;
        if (identityTtl == null || identityTtl.isNegative() || identityTtl.isZero()) {
            identityTtl = Duration.ofSeconds(60);
        }
        if (identityMaxEntries <= 0) identityMaxEntries = 20_000;
        blockedWords = blockedWords == null ? List.of() : List.copyOf(blockedWords);
    }
}
