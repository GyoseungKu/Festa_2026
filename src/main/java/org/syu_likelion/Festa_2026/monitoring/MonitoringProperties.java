package org.syu_likelion.Festa_2026.monitoring;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("monitoring")
public record MonitoringProperties(
        Duration presenceTtl,
        int presenceMaxSessions,
        int dbQueryTimeoutSeconds) {

    public MonitoringProperties {
        if (presenceTtl == null || presenceTtl.isNegative() || presenceTtl.isZero()) {
            presenceTtl = Duration.ofSeconds(150);
        }
        if (presenceMaxSessions <= 0) presenceMaxSessions = 100_000;
        if (dbQueryTimeoutSeconds <= 0) dbQueryTimeoutSeconds = 2;
    }
}
