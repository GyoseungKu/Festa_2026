package org.syu_likelion.Festa_2026.notice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class NoticeDtos {
    private NoticeDtos() { }
    public record NoticeMutationRequest(@NotBlank @Size(max = 150) String title,
            @NotBlank @Size(max = 5000) String content, boolean pinned,
            @io.swagger.v3.oas.annotations.media.Schema(description = "최상단 배너 공지 여부. 등록 시 생략하면 false, 수정 시 생략하면 기존 값 유지")
            Boolean banner) { }
    public record NoticePinUpdateRequest(boolean pinned) { }
    public record NoticeAttachmentResponse(Long id, String url, String originalFilename,
            String contentType, long size, int displayOrder) { }
    public record NoticeResponse(Long id, String title, String content, boolean pinned, boolean banner, long viewCount,
            List<NoticeAttachmentResponse> media, List<NoticeAttachmentResponse> files, String authorName, Instant createdAt, Instant updatedAt) { }
    public record NoticePageResponse(List<NoticeResponse> items, int page, int size,
            long totalElements, int totalPages) { }
}
