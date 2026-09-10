package org.syu_likelion.Festa_2026.lostitem;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class LostItemDtos {
    private LostItemDtos() { }

    public record LostItemMutationRequest(
            @NotBlank @Size(max = 150) String title,
            @NotBlank @Size(max = 5000) String content,
            @NotNull LostItemStatus status,
            boolean pinned,
            @Size(max = 200) String foundLocation) {
        public LostItemMutationRequest(String title, String content, LostItemStatus status, boolean pinned) {
            this(title, content, status, pinned, null);
        }
    }

    public record LostItemImageResponse(Long id, String url, int displayOrder) { }

    public record LostItemStatusUpdateRequest(@NotNull LostItemStatus status) { }

    public record LostItemPinUpdateRequest(boolean pinned) { }

    public record LostItemResponse(
            Long id,
            String title,
            String content,
            String foundLocation,
            LostItemStatus status,
            String statusLabel,
            boolean pinned,
            long viewCount,
            List<LostItemImageResponse> images,
            String authorName,
            Instant createdAt,
            Instant updatedAt) { }

    public record LostItemPageResponse(
            List<LostItemResponse> items,
            int page,
            int size,
            long totalElements,
            int totalPages) { }
}
