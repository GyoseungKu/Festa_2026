package org.syu_likelion.Festa_2026.birthday;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.syu_likelion.Festa_2026.user.FestivalRole;

public final class BirthdayMessageDtos {
    private BirthdayMessageDtos() { }

    public record BirthdayMessageCreateRequest(@NotBlank String content,
            @Schema(description = "쪽지 디자인 번호(1 이상의 정수, 디자인 개수에 따른 상한 없음). 생략/null이면 1.", defaultValue = "1", example = "2")
            @Min(1) Integer designNo) { }
    public record PublicAuthor(String department, String maskedStudentNo, String maskedName) { }
    public record BirthdayMessageResponse(Long id, String content, PublicAuthor author,
                                          long heartCount, boolean heartedByMe, boolean mine,
                                          Instant createdAt, int designNo) { }
    public record BirthdayMessagePageResponse(List<BirthdayMessageResponse> items, int page, int size,
                                              long totalElements, int totalPages,
            @Schema(description = "랜덤 정렬 seed. 다음 페이지에 같은 값을 전달합니다. 다른 정렬에서는 null.")
            Long seed) { }
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
                                               AdminUserView author, int designNo) { }
    public record AdminBirthdayMessagePageResponse(List<AdminBirthdayMessageResponse> items, int page, int size,
                                                   long totalElements, int totalPages) { }
    public record AdminHeartUserResponse(AdminUserView user, Instant heartedAt) { }
    public record AdminHeartPageResponse(Long messageId, long heartCount, List<AdminHeartUserResponse> items,
                                         int page, int size, long totalElements, int totalPages) { }
}
