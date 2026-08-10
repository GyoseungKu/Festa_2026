package org.syu_likelion.Festa_2026.admin;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class AdminCookieManager {
    private static final String PATH = "/admin";
    private final AdminProperties properties;

    public AdminCookieManager(AdminProperties properties) {
        this.properties = properties;
    }

    public String readAccessToken(HttpServletRequest request) { return read(request, properties.accessCookieName()); }
    public String readRefreshToken(HttpServletRequest request) { return read(request, properties.refreshCookieName()); }

    public void setLoginCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie(accessToken).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(refreshToken).toString());
    }

    public void applyRotation(HttpServletResponse response, String accessToken, String refreshToken) {
        if (accessToken != null && !accessToken.isBlank()) {
            response.addHeader(HttpHeaders.SET_COOKIE, accessCookie(accessToken).toString());
        }
        if (refreshToken != null && !refreshToken.isBlank()) {
            response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(refreshToken).toString());
        }
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, clearCookie(properties.accessCookieName()).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, clearCookie(properties.refreshCookieName()).toString());
    }

    private String read(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) if (name.equals(cookie.getName())) return cookie.getValue();
        return null;
    }

    private ResponseCookie accessCookie(String value) { return base(properties.accessCookieName(), value).build(); }
    private ResponseCookie refreshCookie(String value) {
        return base(properties.refreshCookieName(), value).maxAge(properties.refreshCookieMaxAge()).build();
    }
    private ResponseCookie clearCookie(String name) { return base(name, "").maxAge(0).build(); }
    private ResponseCookie.ResponseCookieBuilder base(String name, String value) {
        return ResponseCookie.from(name, value).httpOnly(true).secure(properties.cookieSecure())
                .sameSite(properties.cookieSameSite()).path(PATH);
    }
}
