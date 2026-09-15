package org.syu_likelion.Festa_2026.admin;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.format.annotation.DateTimeFormat;
import org.syu_likelion.Festa_2026.timetable.TimetableDtos.MutationRequest;
import org.syu_likelion.Festa_2026.timetable.TimetableDtos.ScheduleResponse;

public class TimetableAdminForm {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private String title;
    private Long performanceId;
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime startsAt;
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime endsAt;
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime publishedAt;

    public static TimetableAdminForm empty() {
        var form = new TimetableAdminForm();
        var now = LocalDateTime.now(SEOUL).withSecond(0).withNano(0);
        form.startsAt = now.plusHours(1);
        form.endsAt = now.plusHours(2);
        form.publishedAt = now;
        return form;
    }

    public static TimetableAdminForm from(ScheduleResponse item) {
        var form = new TimetableAdminForm();
        form.title = item.title();
        form.startsAt = LocalDateTime.ofInstant(item.startsAt(), SEOUL);
        form.endsAt = LocalDateTime.ofInstant(item.endsAt(), SEOUL);
        form.publishedAt = LocalDateTime.ofInstant(item.publishedAt(), SEOUL);
        form.performanceId = item.performance() == null ? null : item.performance().id();
        return form;
    }

    public MutationRequest toRequest() {
        return new MutationRequest(title, instant(startsAt), instant(endsAt), instant(publishedAt), performanceId);
    }
    private Instant instant(LocalDateTime value) { return value == null ? null : value.atZone(SEOUL).toInstant(); }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Long getPerformanceId() { return performanceId; }
    public void setPerformanceId(Long performanceId) { this.performanceId = performanceId; }
    public LocalDateTime getStartsAt() { return startsAt; }
    public void setStartsAt(LocalDateTime startsAt) { this.startsAt = startsAt; }
    public LocalDateTime getEndsAt() { return endsAt; }
    public void setEndsAt(LocalDateTime endsAt) { this.endsAt = endsAt; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
}
