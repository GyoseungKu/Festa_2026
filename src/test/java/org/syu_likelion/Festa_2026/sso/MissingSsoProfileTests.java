package org.syu_likelion.Festa_2026.sso;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.syu_likelion.Festa_2026.config.SsoProperties;
import org.syu_likelion.Festa_2026.qr.QrService;
import tools.jackson.databind.ObjectMapper;

class MissingSsoProfileTests {
    HttpServer server;
    SsoInternalProfileClient client;
    int status = 200;
    String body = "{\"profiles\":[]}";

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/internal/users/profiles/batch", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        var tokens = mock(SsoServiceTokenProvider.class);
        when(tokens.getToken()).thenReturn("test-token");
        client = new SsoInternalProfileClient(new SsoProperties(
                "http://127.0.0.1:" + server.getAddress().getPort(), "test", "test",
                null, null, null, null), tokens, new ObjectMapper());
    }

    @AfterEach void stop() { if (server != null) server.stop(0); }

    @Test void missingSingleUserReturnsUnknownWithoutPersonalInformation() {
        UUID id = UUID.randomUUID();
        var profile = client.getProfile(id);
        assertThat(profile.userUuid()).isEqualTo(id);
        assertThat(profile.name()).isEqualTo("알 수 없음");
        assertThat(profile.email()).isNull();
        assertThat(profile.studentNo()).isNull();
        assertThat(profile.phone()).isNull();
        assertThat(QrService.maskName(profile.name())).isEqualTo("알 수 없음");
    }

    @Test void mixedBatchPreservesExistingUsersAndFillsOnlyMissingUsers() {
        UUID existing = UUID.randomUUID(), missing = UUID.randomUUID();
        body = "{\"profiles\":[{\"userUuid\":\"" + existing + "\",\"name\":\"홍길동\",\"studentNo\":\"20260001\"}]}";
        var result = client.getProfiles(List.of(existing, missing));
        assertThat(result).extracting(SsoProfiles.InternalUserProfile::name).containsExactly("홍길동", "알 수 없음");
        assertThat(result.get(0).studentNo()).isEqualTo("20260001");
        assertThat(result.get(1).studentNo()).isNull();
    }

    @Test void serverFailureAndMalformedResponsesRemainErrors() {
        status = 500;
        assertThatThrownBy(() -> client.getProfile(UUID.randomUUID())).isInstanceOf(SsoException.class);
        status = 200; body = "{}";
        assertThatThrownBy(() -> client.getProfile(UUID.randomUUID())).isInstanceOf(SsoException.class);
    }
}
