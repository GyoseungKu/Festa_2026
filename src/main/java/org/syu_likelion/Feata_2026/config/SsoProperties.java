package org.syu_likelion.Feata_2026.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sso")
public record SsoProperties(String baseUrl, String clientId, String clientSecret,
                            Duration connectTimeout, Duration readTimeout,
                            String refreshCookieName) {
    public SsoProperties {
        baseUrl = required(baseUrl, "sso.base-url").replaceAll("/+$", "");
        clientId = required(clientId, "sso.client-id");
        clientSecret = required(clientSecret, "sso.client-secret");
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(3) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(5) : readTimeout;
        refreshCookieName = refreshCookieName == null ? "refreshToken" : refreshCookieName;
    }

    private static String required(String value, String key) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(key + " must be configured");
        return value;
    }
}
