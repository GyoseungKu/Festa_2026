package org.syu_likelion.Festa_2026.birthday;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.syu_likelion.Festa_2026.user.FestivalRole;

public final class BirthdayMessageDtos {
    private BirthdayMessageDtos() { }

    public record BirthdayMessageCreateRequest(@NotBlank String content) { }
    public record PublicAuthor(String department, String maskedStudentNo, String maskedName) { }
    public record BirthdayMessageResponse(Long id, String content, PublicAuthor author,
                                          long heartCount, boolean heartedByMe, boolean mine,
                                          Instant createdAt) { }
    public record BirthdayMessagePageResponse(List<BirthdayMessageResponse> items, int page, int size,
                                              long totalElements, int totalPages) { }
    public record MyBirthdayMessageResponse(boolean written,
            @Schema(description = "작성한 활성 쪽지가 없으면 written=false이며 message는 null입니다.", types = {"object", "null"}, schemaResolution = Schema.SchemaResolution.INLINE)
            BirthdayMessageResponse message) { }
    public record HeartResponse(Long messageId, long heartCount, boolean heartedByMe) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AdminUserView(FestivalRole viewerRole, UUID userUuid, String loginId, String email,
                                String ssoRole, String status, String name, String phone, String studentNo,
                                String department, Integer grade, String enrollment, String birthDate,
                                String createdAt, String updatedAt, Set<FestivalRole> festivalRoles) { }
    public record AdminBirthdayMessageResponse(Long id, String content, long heartCount, Instant createdAt,
                                               AdminUserView author) { }
    public record AdminBirthdayMessagePageResponse(List<AdminBirthdayMessageResponse> items, int page, int size,
                                                   long totalElements, int totalPages) { }
    public record AdminHeartUserResponse(AdminUserView user, Instant heartedAt) { }
    public record AdminHeartPageResponse(Long messageId, long heartCount, List<AdminHeartUserResponse> items,
                                         int page, int size, long totalElements, int totalPages) { }
}
