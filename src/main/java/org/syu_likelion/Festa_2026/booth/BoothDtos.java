package org.syu_likelion.Festa_2026.booth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class BoothDtos {
    private BoothDtos() { }

    public record BoothMutationRequest(
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 150) String operator,
            @NotBlank @Size(max = 5000) String description,
            @Schema(description = "시간대 없는 운영 시작 시각(HH:mm:ss).", example = "10:00:00")
            @NotNull LocalTime opensAt,
            @Schema(description = "시간대 없는 운영 종료 시각(HH:mm:ss).", example = "18:00:00")
            @NotNull LocalTime closesAt,
            boolean stampEnabled,
            @Size(max = 100) List<@NotNull UUID> managerUuids) { }

    public record BoothMediaOrderRequest(
            @NotEmpty List<@NotNull Long> mediaIds,
            @NotNull Long representativeMediaId) { }

    public record BoothMediaResponse(Long id, BoothMediaKind kind, String url,
                                     int displayOrder, boolean representative) { }

    public record BoothSummaryResponse(Long id, BigDecimal latitude, BigDecimal longitude,
                                       String name, String operator, LocalTime opensAt, LocalTime closesAt,
                                       boolean stampEnabled, BoothMediaResponse representativeMedia,
                                       boolean favorited) { }

    public record BoothDetailResponse(Long id, BigDecimal latitude, BigDecimal longitude,
                                      String name, String operator, String description,
                                      LocalTime opensAt, LocalTime closesAt, boolean stampEnabled,
                                      List<BoothMediaResponse> media,
                                      BoothMediaResponse representativeMedia, boolean favorited,
                                      Instant createdAt, Instant updatedAt) { }

    public record BoothAdminResponse(BoothDetailResponse booth, List<UUID> managerUuids) { }
}
