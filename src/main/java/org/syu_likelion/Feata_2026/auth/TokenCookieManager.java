package org.syu_likelion.Feata_2026.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.syu_likelion.Feata_2026.config.AuthProperties;

@Component
public class TokenCookieManager {
    private final AuthProperties properties;

    public TokenCookieManager(AuthProperties properties) {
        this.properties = properties;
    }

    public String readRefreshToken(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (properties.refreshCookieName().equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    public String create(String refreshToken) {
        return ResponseCookie.from(properties.refreshCookieName(), refreshToken)
                .httpOnly(true)
                .secure(properties.refreshCookieSecure())
                .sameSite(properties.refreshCookieSameSite())
                .path("/api")
                .maxAge(properties.refreshCookieMaxAge())
                .build().toString();
    }

    public String clear() {
        return ResponseCookie.from(properties.refreshCookieName(), "")
                .httpOnly(true)
                .secure(properties.refreshCookieSecure())
                .sameSite(properties.refreshCookieSameSite())
                .path("/api")
                .maxAge(0)
                .build().toString();
    }

    public static final String SET_COOKIE = HttpHeaders.SET_COOKIE;
}
