package org.syu_likelion.Festa_2026.schoolsso;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.syu_likelion.Festa_2026.error.ApiException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class SchoolSsoClient {
    private static final Logger log = LoggerFactory.getLogger(SchoolSsoClient.class);
    private static final int MAX_CODE_LENGTH = 4096;
    private static final int MAX_JWT_LENGTH = 32_768;
    private static final int MAX_JWKS_LENGTH = 256_000;

    private final SchoolSsoProperties properties;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final HttpClient httpClient;
    private volatile Map<String, CachedKey> keyCache = Map.of();

    public SchoolSsoClient(SchoolSsoProperties properties, ObjectMapper mapper, Clock clock) {
        this.properties = properties;
        this.mapper = mapper;
        this.clock = clock;
        this.httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
    }

    public void requireConfigured() {
        if (!properties.enabled() || blank(properties.clientId()) || blank(properties.clientSecret())
                || blank(properties.authorizeUrl()) || blank(properties.tokenUrl()) || blank(properties.jwksUrl())
                || blank(properties.callbackUrl()) || blank(properties.issuer()) || blank(properties.audience())
                || blank(properties.returnUrl()) || containsLineBreak(properties.clientId())
                || containsLineBreak(properties.clientSecret())) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SCHOOL_SSO_NOT_CONFIGURED",
                    "학교 SSO 연동이 아직 설정되지 않았습니다.");
        }
    }

    public SchoolAcademicProfile exchangeAndVerify(String code) {
        requireConfigured();
        if (blank(code) || code.length() > MAX_CODE_LENGTH) throw invalidCode();
        TokenResponse token = exchangeCode(code);
        if (token == null || blank(token.accessToken()) || token.accessToken().length() > MAX_JWT_LENGTH
                || !"Bearer".equalsIgnoreCase(token.tokenType())) throw upstreamInvalid();
        return verifyJwt(token.accessToken());
    }

    private TokenResponse exchangeCode(String code) {
        String form = "code=" + encode(code) + "&redirect_uri=" + encode(properties.callbackUrl());
        String credentials = properties.clientId() + ":" + properties.clientSecret();
        String authorization = "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.tokenUrl()))
                .timeout(properties.readTimeout())
                .header("Authorization", authorization)
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response = send(request, "token");
        if (response.statusCode() == 400 || response.statusCode() == 401) throw invalidCode();
        if (response.statusCode() == 429) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "SCHOOL_SSO_RATE_LIMITED",
                    "학교 SSO 요청이 많습니다. 잠시 후 다시 시도해 주세요.");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw unavailable();
        try {
            return mapper.readValue(response.body(), TokenResponse.class);
        } catch (JacksonException exception) {
            throw upstreamInvalid();
        }
    }

    private SchoolAcademicProfile verifyJwt(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())) throw tokenInvalid();
            String kid = jwt.getHeader().getKeyID();
            if (blank(kid) || kid.length() > 200) throw tokenInvalid();

            RSAPublicKey publicKey = keyFor(kid, false);
            if (!jwt.verify(new RSASSAVerifier(publicKey))) {
                publicKey = keyFor(kid, true);
                if (!jwt.verify(new RSASSAVerifier(publicKey))) throw tokenInvalid();
            }

            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            validateClaims(claims);
            String studentNo = requiredClaim(claims, "student_id");
            String department = requiredClaim(claims, "department");
            String name = requiredClaim(claims, "name");
            String consentTarget = requiredClaim(claims, "consent_target");
            if (!studentNo.equals(claims.getSubject())) throw tokenInvalid();
            Instant now = clock.instant();
            return new SchoolAcademicProfile(studentNo, department, name, consentTarget,
                    now, now.plus(properties.profileTtl()));
        } catch (ParseException | JOSEException exception) {
            throw tokenInvalid();
        }
    }

    private void validateClaims(JWTClaimsSet claims) {
        Instant now = clock.instant();
        Instant skewedPast = now.minus(properties.clockSkew());
        Instant skewedFuture = now.plus(properties.clockSkew());
        Date expiresAt = claims.getExpirationTime();
        Date notBefore = claims.getNotBeforeTime();
        Date issuedAt = claims.getIssueTime();
        if (!properties.issuer().equals(claims.getIssuer())
                || claims.getAudience() == null || !claims.getAudience().contains(properties.audience())
                || expiresAt == null || !expiresAt.toInstant().isAfter(skewedPast)
                || notBefore == null || notBefore.toInstant().isAfter(skewedFuture)
                || issuedAt == null || issuedAt.toInstant().isAfter(skewedFuture)
                || issuedAt.toInstant().isBefore(now.minus(properties.maximumTokenAge()))
                || expiresAt.before(issuedAt) || blank(claims.getJWTID())
                || claims.getJWTID().length() > 200) throw tokenInvalid();
    }

    private String requiredClaim(JWTClaimsSet claims, String name) throws ParseException {
        String value = claims.getStringClaim(name);
        if (blank(value) || value.length() > 200) throw tokenInvalid();
        return value;
    }

    private RSAPublicKey keyFor(String kid, boolean forceRefresh) {
        CachedKey cached = keyCache.get(kid);
        if (!forceRefresh && cached != null && clock.instant().isBefore(cached.expiresAt())) return cached.key();
        refreshKeys();
        CachedKey refreshed = keyCache.get(kid);
        if (refreshed == null || !clock.instant().isBefore(refreshed.expiresAt())) throw tokenInvalid();
        return refreshed.key();
    }

    private synchronized void refreshKeys() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.jwksUrl()))
                .timeout(properties.readTimeout()).header("Accept", "application/json").GET().build();
        HttpResponse<String> response = send(request, "jwks");
        if (response.statusCode() < 200 || response.statusCode() >= 300
                || response.body() == null || response.body().length() > MAX_JWKS_LENGTH) throw unavailable();
        try {
            JwksResponse jwks = mapper.readValue(response.body(), JwksResponse.class);
            if (jwks == null || jwks.keys() == null || jwks.keys().isEmpty() || jwks.keys().size() > 20) {
                throw upstreamInvalid();
            }
            Instant expiresAt = clock.instant().plus(properties.keyCacheTtl());
            java.util.HashMap<String, CachedKey> refreshed = new java.util.HashMap<>();
            for (JwksKey key : jwks.keys()) {
                if (key == null || blank(key.kid()) || key.kid().length() > 200 || blank(key.pem())) continue;
                refreshed.put(key.kid(), new CachedKey(parseRsaPublicKey(key.pem()), expiresAt));
            }
            if (refreshed.isEmpty()) throw upstreamInvalid();
            keyCache = Map.copyOf(refreshed);
        } catch (JacksonException | java.security.GeneralSecurityException | IllegalArgumentException exception) {
            throw upstreamInvalid();
        }
    }

    private RSAPublicKey parseRsaPublicKey(String pem) throws java.security.GeneralSecurityException {
        String encoded = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "").replaceAll("\\s", "");
        if (encoded.isBlank() || encoded.length() > 16_384) throw new java.security.InvalidKeyException();
        PublicKey key = KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(encoded)));
        if (!(key instanceof RSAPublicKey rsa) || rsa.getModulus().bitLength() < 2048) {
            throw new java.security.InvalidKeyException("RSA key is too small");
        }
        return rsa;
    }

    private HttpResponse<String> send(HttpRequest request, String endpoint) {
        long started = System.nanoTime();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("School SSO endpoint={} status={} durationMs={} success={}", endpoint, response.statusCode(),
                    java.time.Duration.ofNanos(System.nanoTime() - started).toMillis(),
                    response.statusCode() >= 200 && response.statusCode() < 300);
            return response;
        } catch (HttpTimeoutException exception) {
            throw unavailable();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (java.io.IOException exception) {
            throw unavailable();
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private boolean containsLineBreak(String value) {
        return value != null && (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0);
    }

    private ApiException invalidCode() {
        return new ApiException(HttpStatus.BAD_REQUEST, "SCHOOL_SSO_CODE_INVALID",
                "학교 로그인 정보가 만료되었거나 올바르지 않습니다. 다시 시도해 주세요.");
    }

    private ApiException tokenInvalid() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "SCHOOL_SSO_TOKEN_INVALID",
                "학교 SSO 인증정보를 검증할 수 없습니다.");
    }

    private ApiException upstreamInvalid() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "SCHOOL_SSO_BAD_GATEWAY",
                "학교 SSO 응답을 처리할 수 없습니다.");
    }

    private ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SCHOOL_SSO_UNAVAILABLE",
                "학교 SSO에 일시적으로 연결할 수 없습니다.");
    }

    public record TokenResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("access_token") String accessToken,
            @com.fasterxml.jackson.annotation.JsonProperty("token_type") String tokenType,
            @com.fasterxml.jackson.annotation.JsonProperty("expires_in") Long expiresIn) { }
    public record JwksResponse(List<JwksKey> keys) { }
    public record JwksKey(String kid, String pem) { }
    private record CachedKey(RSAPublicKey key, Instant expiresAt) { }
}
