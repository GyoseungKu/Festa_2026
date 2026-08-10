package org.syu_likelion.Festa_2026.analytics;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class FrontendAnalyticsDtos {
    private FrontendAnalyticsDtos() { }

    public record EventBatchRequest(
            @NotNull UUID sessionId,
            @Size(max = 50) String appVersion,
            @NotEmpty @Size(max = 20) List<@Valid FrontendEventRequest> events) { }

    public record FrontendEventRequest(
            @NotNull UUID eventId,
            @NotNull FrontendEventType type,
            @NotBlank @Size(max = 200) String route,
            @Pattern(regexp = "^[A-Za-z0-9_-]{1,100}$") String targetId,
            @NotNull Instant occurredAt,
            Long durationMs) { }

    public record EventBatchResponse(int acceptedEvents) { }
}
