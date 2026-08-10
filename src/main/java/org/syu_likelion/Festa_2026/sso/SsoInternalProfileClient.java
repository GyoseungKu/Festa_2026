package org.syu_likelion.Festa_2026.sso;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.syu_likelion.Festa_2026.config.SsoProperties;
import org.syu_likelion.Festa_2026.sso.SsoProfiles.InternalUserProfile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class SsoInternalProfileClient {
    private static final Logger log = LoggerFactory.getLogger(SsoInternalProfileClient.class);
    private static final String ENDPOINT = "/api/internal/users/profiles/batch";
    private final SsoProperties properties;
    private final SsoServiceTokenProvider tokenProvider;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    public SsoInternalProfileClient(SsoProperties properties, SsoServiceTokenProvider tokenProvider,
                                    ObjectMapper mapper) {
        this.properties = properties;
        this.tokenProvider = tokenProvider;
        this.mapper = mapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
    }

    public InternalUserProfile getProfile(UUID userUuid) {
        List<InternalUserProfile> profiles = getProfiles(List.of(userUuid));
        return profiles.stream().filter(profile -> userUuid.equals(profile.userUuid())).findFirst()
                .orElseThrow(() -> new SsoException(502, "SSO profile response did not contain requested user"));
    }

    public List<InternalUserProfile> getProfiles(List<UUID> userUuids) {
        if (userUuids == null || userUuids.isEmpty()) return List.of();
        String token = tokenProvider.getToken();
        HttpResponse<String> response = request(token, userUuids);
        if (response.statusCode() == 401) {
            tokenProvider.invalidate(token);
            token = tokenProvider.getToken();
            response = request(token, userUuids);
        }
        if (response.statusCode() == 429) throw new SsoException(429, "SSO profile request rate limited");
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new SsoException(response.statusCode() >= 500 ? 503 : 502, "SSO internal profile request failed");
        }
        return parseProfiles(response.body());
    }

    private HttpResponse<String> request(String token, List<UUID> userUuids) {
        try {
            String body = mapper.writeValueAsString(new BatchRequest(userUuids));
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.baseUrl() + ENDPOINT))
                    .timeout(properties.readTimeout())
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("SSO internal-profile endpoint={} status={} requestedUsers={} success={}", ENDPOINT,
                    response.statusCode(), userUuids.size(), response.statusCode() >= 200 && response.statusCode() < 300);
            return response;
        } catch (HttpTimeoutException timeout) {
            throw new SsoException(503, "SSO internal profile request timed out", timeout);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new SsoException(503, "SSO internal profile request interrupted", interrupted);
        } catch (java.io.IOException transport) {
            throw new SsoException(503, "SSO internal profile unavailable", transport);
        } catch (JacksonException jsonFailure) {
            throw new SsoException(502, "Could not encode SSO profile request", jsonFailure);
        }
    }

    private List<InternalUserProfile> parseProfiles(String body) {
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode array = findArray(root);
            if (array == null) throw new SsoException(502, "Invalid SSO profile response");
            List<InternalUserProfile> profiles = new ArrayList<>();
            for (JsonNode node : array) profiles.add(mapper.treeToValue(node, InternalUserProfile.class));
            return List.copyOf(profiles);
        } catch (JacksonException invalidJson) {
            throw new SsoException(502, "Invalid SSO profile response", invalidJson);
        }
    }

    private JsonNode findArray(JsonNode root) {
        if (root.isArray()) return root;
        for (String field : List.of("profiles", "users", "items", "content")) {
            JsonNode value = root.get(field);
            if (value != null && value.isArray()) return value;
        }
        JsonNode data = root.get("data");
        if (data == null) return null;
        if (data.isArray()) return data;
        for (String field : List.of("profiles", "users", "items", "content")) {
            JsonNode value = data.get(field);
            if (value != null && value.isArray()) return value;
        }
        return null;
    }

    private record BatchRequest(List<UUID> userUuids) { }
}
