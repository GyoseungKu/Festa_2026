package org.syu_likelion.Festa_2026.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

class ApiRequestLogFilterTests {
    private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void recordsNormalizedApiRequestWithoutTrustingSpoofedForwardedIp() throws Exception {
        AsyncApiRequestLogWriter writer = mock(AsyncApiRequestLogWriter.class);
        ApiRequestLogFilter filter = new ApiRequestLogFilter(properties(false), writer,
                Clock.fixed(NOW, ZoneOffset.UTC));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/performances/15");
        request.setRemoteAddr("10.0.0.7");
        request.setServerName("festival.example.com");
        request.setScheme("https");
        request.addHeader("X-Forwarded-For", "203.0.113.10");
        request.addHeader("User-Agent", "Festival App\r\nInjected");
        MockHttpServletResponse response = new MockHttpServletResponse();
        UUID userUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174099");

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            request.setAttribute(ApiRequestContext.USER_UUID_ATTRIBUTE, userUuid);
            request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/performances/{id}");
            response.setStatus(200);
        });

        ArgumentCaptor<ApiRequestLogRecord> captor = ArgumentCaptor.forClass(ApiRequestLogRecord.class);
        verify(writer).publish(captor.capture());
        ApiRequestLogRecord record = captor.getValue();
        assertThat(record.userUuid()).isEqualTo(userUuid);
        assertThat(record.clientIp()).isEqualTo("10.0.0.7");
        assertThat(record.requestPath()).isEqualTo("/api/performances/15");
        assertThat(record.routePattern()).isEqualTo("/api/performances/{id}");
        assertThat(record.userAgent()).isEqualTo("Festival AppInjected");
        assertThat(record.occurredAt()).isEqualTo(NOW);
        assertThat(response.getHeader(ApiRequestLogFilter.REQUEST_ID_HEADER)).isEqualTo(record.requestId());
        assertThatCodeIsUuid(record.requestId());
    }

    @Test
    void usesForwardedIpOnlyWhenTrustedProxyModeIsEnabled() throws Exception {
        AsyncApiRequestLogWriter writer = mock(AsyncApiRequestLogWriter.class);
        ApiRequestLogFilter filter = new ApiRequestLogFilter(properties(true), writer,
                Clock.fixed(NOW, ZoneOffset.UTC));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("10.0.0.7");
        request.addHeader("Forwarded", "for=203.0.113.21;proto=https, for=10.0.0.7");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> response.setStatus(401));

        ArgumentCaptor<ApiRequestLogRecord> captor = ArgumentCaptor.forClass(ApiRequestLogRecord.class);
        verify(writer).publish(captor.capture());
        assertThat(captor.getValue().clientIp()).isEqualTo("203.0.113.21");
        assertThat(captor.getValue().responseStatus()).isEqualTo(401);
    }

    @Test
    void ignoresStaticResourcesAndPreflightRequests() throws Exception {
        AsyncApiRequestLogWriter writer = mock(AsyncApiRequestLogWriter.class);
        ApiRequestLogFilter filter = new ApiRequestLogFilter(properties(false), writer,
                Clock.fixed(NOW, ZoneOffset.UTC));

        filter.doFilter(new MockHttpServletRequest("GET", "/images/Logo.webp"),
                new MockHttpServletResponse(), (request, response) -> { });
        filter.doFilter(new MockHttpServletRequest("OPTIONS", "/api/performances"),
                new MockHttpServletResponse(), (request, response) -> { });

        verify(writer, never()).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void samplingDropsOnlySuccessfulApiGetsAndKeepsErrorsAndWrites() throws Exception {
        AsyncApiRequestLogWriter writer = mock(AsyncApiRequestLogWriter.class);
        ApiRequestLogFilter filter = new ApiRequestLogFilter(properties(false, 0), writer,
                Clock.fixed(NOW, ZoneOffset.UTC));

        filter.doFilter(new MockHttpServletRequest("GET", "/api/performances"),
                new MockHttpServletResponse(), (request, response) -> { });
        MockHttpServletRequest failedGet = new MockHttpServletRequest("GET", "/api/performances/404");
        MockHttpServletResponse failedResponse = new MockHttpServletResponse();
        filter.doFilter(failedGet, failedResponse, (request, response) -> failedResponse.setStatus(404));
        filter.doFilter(new MockHttpServletRequest("POST", "/api/performances"),
                new MockHttpServletResponse(), (request, response) -> { });

        verify(writer, org.mockito.Mockito.times(2)).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void authenticatedUserContextUsesRequestScopeAndDoesNotLeakWithoutRequest() {
        UUID userUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174099");
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        ApiRequestContext.markAuthenticatedUser(userUuid);

        assertThat(ApiRequestContext.authenticatedUser(request)).isEqualTo(userUuid);
        RequestContextHolder.resetRequestAttributes();
        ApiRequestContext.markAuthenticatedUser(UUID.randomUUID());
        assertThat(ApiRequestContext.authenticatedUser(request)).isEqualTo(userUuid);
    }

    private ApiRequestLogProperties properties(boolean trustForwarded) {
        return properties(trustForwarded, 1.0);
    }

    private ApiRequestLogProperties properties(boolean trustForwarded, double sampleRate) {
        return new ApiRequestLogProperties(true, 100, 10, Duration.ofMillis(50),
                Duration.ofDays(60), Duration.ofHours(6), 100, 1, sampleRate,
                Duration.ofSeconds(1), trustForwarded);
    }

    private void assertThatCodeIsUuid(String value) {
        assertThat(UUID.fromString(value)).isNotNull();
    }
}
