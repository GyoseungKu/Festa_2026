package org.syu_likelion.Festa_2026.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiRequestLogFilter extends OncePerRequestFilter {
    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    private final ApiRequestLogProperties properties;
    private final AsyncApiRequestLogWriter writer;
    private final Clock clock;

    public ApiRequestLogFilter(ApiRequestLogProperties properties,
                               AsyncApiRequestLogWriter writer, Clock clock) {
        this.properties = properties;
        this.writer = writer;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        request.setAttribute(ApiRequestContext.REQUEST_ID_ATTRIBUTE, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        MDC.put("requestId", requestId);
        long started = System.nanoTime();
        Instant occurredAt = Instant.now(clock);
        boolean completed = false;
        try {
            filterChain.doFilter(request, response);
            completed = true;
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            int status = completed || response.getStatus() >= 400 ? response.getStatus() : 500;
            if (shouldStore(request, status, durationMs)) {
                writer.publish(toRecord(request, status, requestId, durationMs, occurredAt));
            }
            MDC.remove("userUuid");
            MDC.remove("requestId");
        }
    }

    private ApiRequestLogRecord toRecord(HttpServletRequest request, int responseStatus,
                                         String requestId, long durationMs, Instant occurredAt) {
        Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return new ApiRequestLogRecord(requestId, ApiRequestContext.authenticatedUser(request),
                clientIp(request), limited(request.getMethod(), 10), limited(request.getRequestURI(), 1024),
                route == null ? null : limited(route.toString(), 512), responseStatus, durationMs,
                limited(request.getServerName(), 255), limited(request.getScheme(), 10),
                nullableLimited(request.getHeader("User-Agent"), 512), occurredAt);
    }

    private boolean shouldStore(HttpServletRequest request, int status, long durationMs) {
        if (status >= 400 || durationMs >= properties.slowRequestThreshold().toMillis()) return true;
        if (!"GET".equalsIgnoreCase(request.getMethod()) || request.getRequestURI().startsWith("/admin")) {
            return true;
        }
        double rate = properties.successfulReadSampleRate();
        return rate >= 1 || (rate > 0 && ThreadLocalRandom.current().nextDouble() < rate);
    }

    private String clientIp(HttpServletRequest request) {
        if (properties.trustForwardedHeaders()) {
            String forwarded = forwardedFor(request.getHeader("Forwarded"));
            if (forwarded != null) return limited(forwarded, 45);
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                return limited(cleanIp(xForwardedFor.split(",", 2)[0]), 45);
            }
        }
        return limited(cleanIp(request.getRemoteAddr()), 45);
    }

    private String forwardedFor(String header) {
        if (header == null || header.isBlank()) return null;
        String firstProxyEntry = header.split(",", 2)[0];
        for (String part : firstProxyEntry.split(";")) {
            String trimmed = part.trim();
            if (trimmed.toLowerCase(Locale.ROOT).startsWith("for=")) {
                return cleanIp(trimmed.substring(4));
            }
        }
        return null;
    }

    private String cleanIp(String value) {
        if (value == null || value.isBlank()) return "unknown";
        String cleaned = value.trim().replace("\"", "");
        if (cleaned.startsWith("[")) {
            int closing = cleaned.indexOf(']');
            if (closing > 0) return cleaned.substring(1, closing);
        }
        int colon = cleaned.indexOf(':');
        if (colon > 0 && cleaned.indexOf(':', colon + 1) < 0) return cleaned.substring(0, colon);
        return cleaned;
    }

    private String limited(String value, int maxLength) {
        if (value == null) return "";
        int limit = Math.min(value.length(), maxLength);
        StringBuilder sanitized = null;
        for (int index = 0; index < limit; index++) {
            char character = value.charAt(index);
            if (character < 0x20 || character == 0x7f) {
                if (sanitized == null) sanitized = new StringBuilder(value.substring(0, index));
            } else if (sanitized != null) {
                sanitized.append(character);
            }
        }
        return sanitized == null ? value.substring(0, limit) : sanitized.toString();
    }

    private String nullableLimited(String value, int maxLength) {
        return value == null || value.isBlank() ? null : limited(value, maxLength);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.enabled() || "OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        String path = request.getRequestURI();
        if (path.startsWith("/admin/assets/")) return true;
        return !(path.equals("/admin") || path.startsWith("/admin/") || path.startsWith("/api/"));
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() { return true; }

    @Override
    protected boolean shouldNotFilterErrorDispatch() { return true; }
}
