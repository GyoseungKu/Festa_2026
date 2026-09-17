package org.syu_likelion.Festa_2026.qr;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.time.Instant;
import java.util.Set;
import java.util.List;
import java.util.UUID;
import org.syu_likelion.Festa_2026.user.FestivalRole;
import org.syu_likelion.Festa_2026.user.SchoolVerificationStatus;

public final class QrDtos {
    private QrDtos() { }

    public record QrTokenResponse(String token, Instant expiresAt) { }

    public record QrScanRequest(@NotBlank @Size(max = 128) String token) { }
    public record UserSearchRequest(@NotBlank @Size(min = 2, max = 100) String query,
                                    @Min(0) Integer page, @Min(1) @Max(100) Integer size) { }
    public record UserSearchResponse(List<QrUserView> items, int page, int size,
                                     long totalElements, int totalPages) { }
    public record UserRoleUpdateRequest(@NotNull FestivalRole managementRole) { }
    public record UserRoleUpdateResponse(UUID userUuid, Set<FestivalRole> festivalRoles) { }
    public record UserSchoolVerificationUpdateRequest(@NotNull Boolean verified) { }
    public record UserSchoolVerificationUpdateResponse(UUID userUuid,
                                                        SchoolVerificationStatus schoolVerificationStatus,
                                                        boolean schoolVerified,
                                                        Instant schoolVerifiedAt, boolean studentFeePaid) {
        public UserSchoolVerificationUpdateResponse(UUID userUuid, SchoolVerificationStatus status, boolean verified, Instant at) {
            this(userUuid, status, verified, at, false);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @io.swagger.v3.oas.annotations.media.Schema(description = "조회자의 축제 권한별 응답. 숨긴 필드와 null은 JSON에서 생략합니다. 학생 인증·납부 여부는 STAFF·ADMIN·SUPER_ADMIN만 조회합니다. wristband와 managementRole은 JSON 필드가 아닙니다. 본인 팔찌 상태는 GET /api/users/me/wristband를 사용합니다.")
    public record QrUserView(
            FestivalRole viewerRole,
            UUID userUuid,
            String loginId,
            String email,
            String ssoRole,
            String status,
            String name,
            String phone,
            String studentNo,
            String department,
            Integer grade,
            String enrollment,
            String birthDate,
            String createdAt,
            String updatedAt,
            Set<FestivalRole> festivalRoles,
            @io.swagger.v3.oas.annotations.media.Schema(description = "STAFF 이상만 포함. 필드 누락은 미인증(false)과 다릅니다.") SchoolVerificationStatus schoolVerificationStatus,
            Instant schoolVerifiedAt,
            @io.swagger.v3.oas.annotations.media.Schema(description = "STAFF 이상만 포함. 필드 누락을 미납부(false)로 표시하지 않습니다.") Boolean studentFeePaid,
            @com.fasterxml.jackson.annotation.JsonIgnore
            org.syu_likelion.Festa_2026.wristband.WristbandService.MyStatus wristband) {
        public QrUserView {
            if (viewerRole != FestivalRole.STAFF && viewerRole != FestivalRole.ADMIN
                    && viewerRole != FestivalRole.SUPER_ADMIN) {
                schoolVerificationStatus = null;
                schoolVerifiedAt = null;
                studentFeePaid = null;
                wristband = null;
            }
        }
        public QrUserView(FestivalRole viewerRole, UUID userUuid, String loginId, String email,
                          String ssoRole, String status, String name, String phone, String studentNo,
                          String department, Integer grade, String enrollment, String birthDate,
                          String createdAt, String updatedAt, Set<FestivalRole> festivalRoles,
                          SchoolVerificationStatus verificationStatus, Instant verifiedAt, boolean studentFeePaid) {
            this(viewerRole, userUuid, loginId, email, ssoRole, status, name, phone, studentNo,
                    department, grade, enrollment, birthDate, createdAt, updatedAt, festivalRoles,
                    verificationStatus, verifiedAt, studentFeePaid, null);
        }
        public QrUserView withWristband(org.syu_likelion.Festa_2026.wristband.WristbandService.MyStatus value) {
            return new QrUserView(viewerRole, userUuid, loginId, email, ssoRole, status, name, phone,
                    studentNo, department, grade, enrollment, birthDate, createdAt, updatedAt, festivalRoles,
                    schoolVerificationStatus, schoolVerifiedAt, studentFeePaid, value);
        }
        public QrUserView(FestivalRole viewerRole, UUID userUuid, String loginId, String email,
                          String ssoRole, String status, String name, String phone, String studentNo,
                          String department, Integer grade, String enrollment, String birthDate,
                          String createdAt, String updatedAt, Set<FestivalRole> festivalRoles,
                          SchoolVerificationStatus verificationStatus, Instant verifiedAt) {
            this(viewerRole, userUuid, loginId, email, ssoRole, status, name, phone, studentNo,
                    department, grade, enrollment, birthDate, createdAt, updatedAt, festivalRoles,
                    verificationStatus, verifiedAt, false);
        }
        public QrUserView(FestivalRole viewerRole, UUID userUuid, String loginId, String email,
                          String ssoRole, String status, String name, String phone, String studentNo,
                          String department, Integer grade, String enrollment, String birthDate,
                          String createdAt, String updatedAt, Set<FestivalRole> festivalRoles) {
            this(viewerRole, userUuid, loginId, email, ssoRole, status, name, phone, studentNo,
                    department, grade, enrollment, birthDate, createdAt, updatedAt, festivalRoles,
                    SchoolVerificationStatus.UNVERIFIED, null);
        }

        @com.fasterxml.jackson.annotation.JsonProperty("schoolVerified")
        public Boolean schoolVerified() {
            if (schoolVerificationStatus == null) return null;
            return schoolVerificationStatus == SchoolVerificationStatus.VERIFIED && schoolVerifiedAt != null;
        }
        public FestivalRole managementRole() {
            if (festivalRoles == null) return null;
            if (festivalRoles.contains(FestivalRole.SUPER_ADMIN)) return FestivalRole.SUPER_ADMIN;
            if (festivalRoles.contains(FestivalRole.ADMIN)) return FestivalRole.ADMIN;
            if (festivalRoles.contains(FestivalRole.STAFF)) return FestivalRole.STAFF;
            return festivalRoles.contains(FestivalRole.USER) ? FestivalRole.USER : null;
        }
    }
}
