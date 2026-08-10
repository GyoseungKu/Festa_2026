package org.syu_likelion.Festa_2026.admin;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("admin")
public record AdminProperties(
        String accessCookieName,
        String refreshCookieName,
        boolean cookieSecure,
        String cookieSameSite,
        Duration refreshCookieMaxAge) {
    public AdminProperties {
        accessCookieName = accessCookieName == null ? "festivalAdminAccess" : accessCookieName;
        refreshCookieName = refreshCookieName == null ? "festivalAdminRefresh" : refreshCookieName;
        cookieSameSite = cookieSameSite == null ? "Strict" : cookieSameSite;
        refreshCookieMaxAge = refreshCookieMaxAge == null ? Duration.ofDays(7) : refreshCookieMaxAge;
    }
}
