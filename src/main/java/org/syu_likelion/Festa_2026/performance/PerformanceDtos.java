package org.syu_likelion.Festa_2026.performance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class PerformanceDtos {
    private PerformanceDtos() { }

    public record PerformanceMutationRequest(
            @NotNull PerformanceCategory category,
            @NotBlank @Size(max = 150) String teamName,
            @NotEmpty @Size(max = 100) List<@NotBlank @Size(max = 100) String> memberNames,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt,
            @NotBlank @Size(max = 5000) String description,
            @Size(max = 3) List<@NotBlank @Size(max = 2048) String> links,
            @Size(max = 3) List<@NotBlank @Size(max = 2048) String> imageUrls,
            @Size(max = 3) List<@NotBlank @Size(max = 2048) String> videoUrls,
            @NotNull Instant publishedAt) { }

    public record MediaResponse(
            Long id,
            PerformanceMediaKind kind,
            PerformanceMediaSource source,
            String url) { }

    public record PerformanceResponse(
            Long id,
            PerformanceCategory category,
            String categoryLabel,
            String teamName,
            List<String> memberNames,
            Instant startsAt,
            Instant endsAt,
            String description,
            List<String> links,
            List<MediaResponse> images,
            List<MediaResponse> videos,
            Instant publishedAt,
            boolean published,
            Instant createdAt,
            Instant updatedAt) { }
}
