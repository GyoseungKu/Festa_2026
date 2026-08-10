package org.syu_likelion.Festa_2026.sso;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.syu_likelion.Festa_2026.auth.AuthDtos.EmailCodeRequest;
import org.syu_likelion.Festa_2026.auth.AuthDtos.EmailRequest;
import org.syu_likelion.Festa_2026.auth.AuthDtos.LoginRequest;
import org.syu_likelion.Festa_2026.auth.AuthDtos.SignupRequest;
import org.syu_likelion.Festa_2026.auth.AuthDtos.SignupResponse;
import org.syu_likelion.Festa_2026.auth.AuthDtos.TokenResponse;
import org.syu_likelion.Festa_2026.config.SsoProperties;
import org.syu_likelion.Festa_2026.user.UserDtos.MeResponse;
import org.syu_likelion.Festa_2026.user.UserDtos.PasswordChangeRequest;
import org.syu_likelion.Festa_2026.user.UserDtos.ProfileUpdateRequest;
import org.syu_likelion.Festa_2026.logging.ApiRequestContext;

@Component
public class SsoAuthClient {
    private static final Logger log = LoggerFactory.getLogger(SsoAuthClient.class);
    private final SsoProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;
    private final String basicAuthorization;

    public SsoAuthClient(SsoProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        String credentials = properties.clientId() + ":" + properties.clientSecret();
        this.basicAuthorization = "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    public void sendSignupEmailCode(EmailRequest request) {
        send("POST", "/api/auth/email/send", Map.of("email", request.email(), "purpose", "SIGNUP"), basicAuthorization, null, Void.class);
    }

    public void verifySignupEmailCode(EmailCodeRequest request) {
        send("POST", "/api/auth/email/verify", request, basicAuthorization, null, Void.class);
    }

    public SignupResponse register(SignupRequest request) {
        return send("POST", "/api/auth/register", request, basicAuthorization, null, SignupResponse.class).body();
    }

    public SsoResult<TokenResponse> login(LoginRequest request) {
        return send("POST", "/api/auth/login", request, basicAuthorization, null, TokenResponse.class);
    }

    public SsoResult<TokenResponse> refresh(String refreshToken) {
        validateHeaderValue(refreshToken);
        return send("POST", "/api/auth/token/refresh", null, null,
                properties.refreshCookieName() + "=" + refreshToken, TokenResponse.class);
    }

    public void logout(String accessToken) {
        send("POST", "/api/auth/logout", null, bearer(accessToken), null, Void.class);
    }

    public MeResponse getMe(String accessToken) {
        return send("GET", "/api/users/me", null, bearer(accessToken), null, MeResponse.class).body();
    }

    public void updateProfile(String accessToken, ProfileUpdateRequest request) {
        send("PATCH", "/api/users/me/profile", request, bearer(accessToken), null, Void.class);
    }

    public void sendNewEmailCode(String accessToken, org.syu_likelion.Festa_2026.user.UserDtos.EmailRequest request) {
        send("POST", "/api/users/me/email/verification", request, bearer(accessToken), null, Void.class);
    }

    public void verifyNewEmailCode(String accessToken, org.syu_likelion.Festa_2026.user.UserDtos.EmailCodeRequest request) {
        send("POST", "/api/users/me/email/verification/confirm", request, bearer(accessToken), null, Void.class);
    }

    public void changeEmail(String accessToken, org.syu_likelion.Festa_2026.user.UserDtos.EmailRequest request) {
        send("PATCH", "/api/users/me/email", request, bearer(accessToken), null, Void.class);
    }

    public void changePassword(String accessToken, PasswordChangeRequest request) {
        send("PATCH", "/api/users/me/password", request, bearer(accessToken), null, Void.class);
    }

    public void withdraw(String accessToken) {
        send("DELETE", "/api/users/me", null, bearer(accessToken), null, Void.class);
    }

    private String bearer(String accessToken) {
        validateHeaderValue(accessToken);
        return "Bearer " + accessToken;
    }

    private void validateHeaderValue(String value) {
        if (value == null || value.isBlank() || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new SsoException(401, "Missing or invalid token");
        }
    }

    private <T> SsoResult<T> send(String method, String path, Object body, String authorization,
                                  String cookie, Class<T> responseType) {
        String correlationId = ApiRequestContext.currentRequestId().orElseGet(() -> UUID.randomUUID().toString());
        long started = System.nanoTime();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(properties.baseUrl() + path))
                    .timeout(properties.readTimeout())
                    .header("Accept", "application/json")
                    .header("X-Correlation-ID", correlationId);
            if (authorization != null) builder.header("Authorization", authorization);
            if (cookie != null) builder.header("Cookie", cookie);
            if (body == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(toJson(body)));
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            logResult(path, response.statusCode(), started, correlationId);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new SsoException(response.statusCode(), "SSO request failed");
            }
            T parsed = responseType == Void.class ? null : fromJson(response.body(), responseType);
            return new SsoResult<>(parsed, extractRefreshToken(response.headers().allValues("Set-Cookie")));
        } catch (HttpTimeoutException timeout) {
            logResult(path, 503, started, correlationId);
            throw new SsoException(503, "SSO request timed out", timeout);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new SsoException(503, "SSO request interrupted", interrupted);
        } catch (java.io.IOException transport) {
            logResult(path, 503, started, correlationId);
            throw new SsoException(503, "SSO is unavailable", transport);
        }
    }

    private String toJson(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JacksonException exception) { throw new SsoException(502, "Could not encode SSO request", exception); }
    }

    private <T> T fromJson(String value, Class<T> type) {
        try { return mapper.readValue(value, type); }
        catch (JacksonException exception) { throw new SsoException(502, "Invalid SSO response", exception); }
    }

    private String extractRefreshToken(List<String> setCookies) {
        String prefix = properties.refreshCookieName() + "=";
        for (String header : setCookies) {
            String firstPart = header.split(";", 2)[0].trim();
            if (firstPart.startsWith(prefix)) return firstPart.substring(prefix.length());
        }
        return null;
    }

    private void logResult(String path, int status, long started, String correlationId) {
        long millis = Duration.ofNanos(System.nanoTime() - started).toMillis();
        log.info("SSO endpoint={} status={} durationMs={} correlationId={} success={}",
                path, status, millis, correlationId, status >= 200 && status < 300);
    }
}
