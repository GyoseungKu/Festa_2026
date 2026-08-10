package org.syu_likelion.Festa_2026.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sso")
public record SsoProperties(String baseUrl, String clientId, String clientSecret,
                            Duration connectTimeout, Duration readTimeout,
                            String refreshCookieName, String serviceScopes) {
    public SsoProperties {
        baseUrl = required(baseUrl, "sso.base-url").replaceAll("/+$", "");
        clientId = required(clientId, "sso.client-id");
        clientSecret = required(clientSecret, "sso.client-secret");
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(3) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(5) : readTimeout;
        refreshCookieName = refreshCookieName == null ? "refreshToken" : refreshCookieName;
        serviceScopes = serviceScopes == null || serviceScopes.isBlank()
                ? "user.email.read user.profile.read" : serviceScopes;
    }

    private static String required(String value, String key) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(key + " must be configured");
        return value;
    }
}
