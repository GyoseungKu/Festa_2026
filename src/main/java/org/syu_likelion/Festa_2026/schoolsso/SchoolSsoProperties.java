package org.syu_likelion.Festa_2026.schoolsso;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "school-sso")
public record SchoolSsoProperties(
        boolean enabled,
        String clientId,
        String clientSecret,
        String authorizeUrl,
        String tokenUrl,
        String jwksUrl,
        String callbackUrl,
        String issuer,
        String audience,
        String returnUrl,
        Duration connectTimeout,
        Duration readTimeout,
        Duration stateTtl,
        Duration profileTtl,
        Duration keyCacheTtl,
        Duration clockSkew,
        Duration maximumTokenAge) {
    public SchoolSsoProperties {
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(3) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(5) : readTimeout;
        stateTtl = stateTtl == null ? Duration.ofMinutes(10) : stateTtl;
        profileTtl = profileTtl == null ? Duration.ofMinutes(15) : profileTtl;
        keyCacheTtl = keyCacheTtl == null ? Duration.ofHours(1) : keyCacheTtl;
        clockSkew = clockSkew == null ? Duration.ofSeconds(30) : clockSkew;
        maximumTokenAge = maximumTokenAge == null ? Duration.ofMinutes(10) : maximumTokenAge;
    }
}
