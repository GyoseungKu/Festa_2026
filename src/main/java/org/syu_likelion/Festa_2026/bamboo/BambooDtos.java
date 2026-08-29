package org.syu_likelion.Festa_2026.bamboo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BambooDtos {
    private BambooDtos() { }

    public record BambooRoomResponse(boolean enabled, boolean readOnly, Instant closesAt,
                                     String nickname, long cursor) { }

    /** 삭제·숨김 메시지는 {@code content}가 null이다. 클라이언트는 {@code status}로 추가·제거를 판단한다. */
    public record BambooMessageResponse(Long id, long seq, String anonName, String content,
                                        BambooMessageStatus status, Instant createdAt, boolean mine) { }

    /** 스트림과 과거 조회가 같은 응답을 쓴다. {@code cursor}는 이어서 스트림을 받을 시작점이다. */
    public record BambooStreamResponse(long cursor, List<BambooMessageResponse> messages) { }

    public record BambooMessageCreateRequest(@NotBlank String content) { }

    public record BambooNicknameRequest(@NotBlank String nickname) { }

    public record BambooNicknameResponse(String nickname) { }

    public record BambooReportRequest(@NotNull BambooReportReason reason) { }

    // ---------------------------------------------------------------- 관리자

    /** 관리자에게는 삭제·숨김된 메시지의 본문도 보여준다. 무엇을 지웠는지 확인해야 하기 때문이다. */
    public record BambooAdminMessageResponse(Long id, long seq, String anonName, String content,
                                             BambooMessageStatus status, int reportCount,
                                             Map<BambooReportReason, Long> reasons, Instant createdAt) { }

    public record BambooAdminPageResponse(List<BambooAdminMessageResponse> items, int page, int size,
                                          long totalElements, int totalPages) { }

    public record BambooStatusChangeRequest(@NotNull List<Long> ids, @NotNull BambooMessageStatus status) { }

    public record BambooMuteRequest(@NotNull Integer minutes) { }

    public record BambooMuteResponse(Instant mutedUntil) { }

    public record BambooRenameRequest(@NotBlank String nickname) { }

    public record BambooSettingsRequest(Boolean enabled, Boolean readOnly, Instant closesAt,
                                        Boolean clearClosesAt) { }

    public record BambooSettingsResponse(boolean enabled, boolean readOnly, Instant closesAt,
                                         Instant updatedAt) { }

    /** SUPER_ADMIN 전용. 신고 처리 과정에서 신원을 확인할 때만 사용하며 조회 사실은 감사 로그에 남는다. */
    public record BambooAuthorResponse(UUID userUuid, String nickname, String loginId, String email,
                                       String name, String phone, String studentNo, String department,
                                       Integer grade, String enrollment) { }
}
