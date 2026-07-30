package org.syu_likelion.Feata_2026.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("auth")
public record AuthProperties(String refreshCookieName, boolean refreshCookieSecure,
                             String refreshCookieSameSite, Duration refreshCookieMaxAge) {
    public AuthProperties {
        refreshCookieName = refreshCookieName == null ? "festivalRefreshToken" : refreshCookieName;
        refreshCookieSameSite = refreshCookieSameSite == null ? "Strict" : refreshCookieSameSite;
        refreshCookieMaxAge = refreshCookieMaxAge == null ? Duration.ofDays(7) : refreshCookieMaxAge;
    }
}
