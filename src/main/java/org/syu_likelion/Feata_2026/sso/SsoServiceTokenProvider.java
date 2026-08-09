package org.syu_likelion.Feata_2026.sso;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.syu_likelion.Feata_2026.config.SsoProperties;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class SsoServiceTokenProvider {
    private static final Logger log = LoggerFactory.getLogger(SsoServiceTokenProvider.class);
    private static final long DEFAULT_EXPIRES_IN = 300;
    private final SsoProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;
    private final Clock clock;
    private volatile CachedToken cachedToken;

    public SsoServiceTokenProvider(SsoProperties properties, ObjectMapper mapper, Clock clock) {
        this.properties = properties;
        this.mapper = mapper;
        this.clock = clock;
        this.httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
    }

    public String getToken() {
        CachedToken current = cachedToken;
        if (isUsable(current)) return current.accessToken();
        synchronized (this) {
            current = cachedToken;
            if (isUsable(current)) return current.accessToken();
            cachedToken = requestToken();
            return cachedToken.accessToken();
        }
    }

    public void invalidate(String accessToken) {
        CachedToken current = cachedToken;
        if (current != null && current.accessToken().equals(accessToken)) cachedToken = null;
    }

    private boolean isUsable(CachedToken token) {
        return token != null && token.expiresAt().isAfter(clock.instant().plusSeconds(30));
    }

    private CachedToken requestToken() {
        String endpoint = "/oauth2/token";
        String tokenUri = properties.baseUrl() + endpoint
                + "?grant_type=client_credentials&scope=" + encodeScopes(properties.serviceScopes());
        String basic = Base64.getEncoder().encodeToString(
                (properties.clientId() + ":" + properties.clientSecret()).getBytes(StandardCharsets.UTF_8));
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(tokenUri))
                    .timeout(properties.readTimeout())
                    .header("Authorization", "Basic " + basic)
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("SSO service-token endpoint={} status={} success={}", endpoint, response.statusCode(),
                    response.statusCode() >= 200 && response.statusCode() < 300);
            if (response.statusCode() == 429) throw new SsoException(429, "SSO service token rate limited");
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("SSO service-token rejected status={} error={}", response.statusCode(), oauthError(response.body()));
                throw new SsoException(response.statusCode() >= 500 ? 503 : 502, "SSO service token request failed");
            }
            JsonNode root = mapper.readTree(response.body());
            String accessToken = text(root, "access_token", "accessToken");
            long expiresIn = number(root, DEFAULT_EXPIRES_IN, "expires_in", "expiresIn");
            if (accessToken == null || accessToken.isBlank()) throw new SsoException(502, "SSO service token missing");
            return new CachedToken(accessToken, clock.instant().plusSeconds(Math.max(1, expiresIn)));
        } catch (HttpTimeoutException timeout) {
            throw new SsoException(503, "SSO service token request timed out", timeout);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new SsoException(503, "SSO service token request interrupted", interrupted);
        } catch (java.io.IOException transport) {
            throw new SsoException(503, "SSO service token unavailable", transport);
        } catch (JacksonException invalidJson) {
            throw new SsoException(502, "Invalid SSO service token response", invalidJson);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String encodeScopes(String value) {
        String normalized = String.join(" ", value.trim().split("[,\\s]+"));
        return encode(normalized).replace("+", "%20");
    }

    private String oauthError(String body) {
        if (body == null || body.isBlank()) return "empty_response";
        try {
            JsonNode root = mapper.readTree(body);
            String error = text(root, "error", "code");
            String description = text(root, "error_description", "message");
            if (error == null) return "unrecognized_response";
            return description == null || description.isBlank() ? error : error + ": " + description;
        } catch (JacksonException ignored) {
            return "non_json_response";
        }
    }

    private String text(JsonNode root, String... names) {
        for (String name : names) {
            JsonNode node = root.get(name);
            if (node != null && !node.isNull()) return node.asText();
        }
        return null;
    }

    private long number(JsonNode root, long defaultValue, String... names) {
        for (String name : names) {
            JsonNode node = root.get(name);
            if (node != null && node.isNumber()) return node.asLong();
        }
        return defaultValue;
    }

    private record CachedToken(String accessToken, Instant expiresAt) { }
}
