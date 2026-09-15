package org.syu_likelion.Festa_2026.timetable;

import jakarta.validation.constraints.*;
import java.time.Instant;

public final class TimetableDtos {
    private TimetableDtos() { }

    public record MutationRequest(
            @NotBlank @Size(max = 150) String title,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt,
            @NotNull Instant publishedAt,
            @Positive Long performanceId) { }

    public record PerformanceLink(Long id, String teamName) { }

    public record ScheduleResponse(Long id, String title, Instant startsAt, Instant endsAt,
                                   Instant publishedAt, boolean published, PerformanceLink performance) { }
}
