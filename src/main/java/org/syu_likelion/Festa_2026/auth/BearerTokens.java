package org.syu_likelion.Festa_2026.auth;

import org.springframework.http.HttpStatus;
import org.syu_likelion.Festa_2026.error.ApiException;

public final class BearerTokens {
    private BearerTokens() { }

    public static String require(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() <= 7) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Access Token이 필요합니다.");
        }
        String token = authorization.substring(7).trim();
        if (token.isEmpty() || token.indexOf('\r') >= 0 || token.indexOf('\n') >= 0) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Access Token이 올바르지 않습니다.");
        }
        return token;
    }
}
