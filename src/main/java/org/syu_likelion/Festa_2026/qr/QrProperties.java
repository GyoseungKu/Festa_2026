package org.syu_likelion.Festa_2026.qr;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("qr")
public record QrProperties(Duration tokenTtl) {
    public QrProperties {
        tokenTtl = tokenTtl == null ? Duration.ofSeconds(60) : tokenTtl;
        if (tokenTtl.isNegative() || tokenTtl.isZero() || tokenTtl.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalArgumentException("qr.token-ttl must be greater than zero and at most 5 minutes");
        }
    }
}
