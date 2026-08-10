package org.syu_likelion.Festa_2026.admin;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;
import org.syu_likelion.Festa_2026.performance.PerformanceCategory;
import org.syu_likelion.Festa_2026.performance.PerformanceDtos.PerformanceMutationRequest;
import org.syu_likelion.Festa_2026.performance.PerformanceDtos.PerformanceResponse;
import org.syu_likelion.Festa_2026.performance.PerformanceMediaSource;

public class PerformanceAdminForm {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private PerformanceCategory category;
    private String teamName;
    private String memberNamesText;
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime startsAt;
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime endsAt;
    private String description;
    private List<String> links = blanks();
    private List<String> imageUrls = blanks();
    private List<String> videoUrls = blanks();
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime publishedAt;
    private List<Long> removeMediaIds = new ArrayList<>();
    private List<MultipartFile> imageFiles = new ArrayList<>();
    private List<MultipartFile> videoFiles = new ArrayList<>();

    public static PerformanceAdminForm empty() {
        PerformanceAdminForm form = new PerformanceAdminForm();
        LocalDateTime now = LocalDateTime.now(SEOUL).withSecond(0).withNano(0);
        form.category = PerformanceCategory.CELEBRITY;
        form.startsAt = now.plusHours(1);
        form.endsAt = now.plusHours(2);
        form.publishedAt = now;
        return form;
    }

    public static PerformanceAdminForm from(PerformanceResponse response) {
        PerformanceAdminForm form = new PerformanceAdminForm();
        form.category = response.category();
        form.teamName = response.teamName();
        form.memberNamesText = String.join("\n", response.memberNames());
        form.startsAt = LocalDateTime.ofInstant(response.startsAt(), SEOUL);
        form.endsAt = LocalDateTime.ofInstant(response.endsAt(), SEOUL);
        form.description = response.description();
        form.links = padded(response.links());
        form.imageUrls = padded(response.images().stream()
                .filter(media -> media.source() == PerformanceMediaSource.LINK)
                .map(media -> media.url()).toList());
        form.videoUrls = padded(response.videos().stream()
                .filter(media -> media.source() == PerformanceMediaSource.LINK)
                .map(media -> media.url()).toList());
        form.publishedAt = LocalDateTime.ofInstant(response.publishedAt(), SEOUL);
        return form;
    }

    public PerformanceMutationRequest toRequest() {
        List<String> members = memberNamesText == null ? List.of() : Arrays.stream(
                        memberNamesText.split("\\r?\\n|,"))
                .map(String::trim).filter(value -> !value.isBlank()).toList();
        return new PerformanceMutationRequest(category, teamName, members,
                instant(startsAt), instant(endsAt), description,
                cleaned(links), cleaned(imageUrls), cleaned(videoUrls), instant(publishedAt));
    }

    private Instant instant(LocalDateTime value) {
        return value == null ? null : value.atZone(SEOUL).toInstant();
    }

    private static List<String> cleaned(List<String> values) {
        return values == null ? List.of() : values.stream()
                .filter(value -> value != null && !value.isBlank()).map(String::trim).toList();
    }

    private static List<String> blanks() {
        return new ArrayList<>(List.of("", "", ""));
    }

    private static List<String> padded(List<String> values) {
        List<String> result = new ArrayList<>(values == null ? List.of() : values);
        while (result.size() < 3) result.add("");
        return result;
    }

    public PerformanceCategory getCategory() { return category; }
    public void setCategory(PerformanceCategory category) { this.category = category; }
    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }
    public String getMemberNamesText() { return memberNamesText; }
    public void setMemberNamesText(String memberNamesText) { this.memberNamesText = memberNamesText; }
    public LocalDateTime getStartsAt() { return startsAt; }
    public void setStartsAt(LocalDateTime startsAt) { this.startsAt = startsAt; }
    public LocalDateTime getEndsAt() { return endsAt; }
    public void setEndsAt(LocalDateTime endsAt) { this.endsAt = endsAt; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<String> getLinks() { return links; }
    public void setLinks(List<String> links) { this.links = links; }
    public List<String> getImageUrls() { return imageUrls; }
    public void setImageUrls(List<String> imageUrls) { this.imageUrls = imageUrls; }
    public List<String> getVideoUrls() { return videoUrls; }
    public void setVideoUrls(List<String> videoUrls) { this.videoUrls = videoUrls; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public List<Long> getRemoveMediaIds() { return removeMediaIds; }
    public void setRemoveMediaIds(List<Long> removeMediaIds) { this.removeMediaIds = removeMediaIds; }
    public List<MultipartFile> getImageFiles() { return imageFiles; }
    public void setImageFiles(List<MultipartFile> imageFiles) { this.imageFiles = imageFiles; }
    public List<MultipartFile> getVideoFiles() { return videoFiles; }
    public void setVideoFiles(List<MultipartFile> videoFiles) { this.videoFiles = videoFiles; }
}
