package org.syu_likelion.Festa_2026.user;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public final class UserDtos {
    private UserDtos() { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MeResponse(
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
            LocalDate birthDate,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Set<FestivalRole> festivalRoles,
            SchoolVerificationStatus schoolVerificationStatus,
            Instant schoolVerifiedAt, Boolean studentFeePaid) {

        public MeResponse {
            // 기존 SSO 응답에는 축제 전용 납부 필드가 없다.
            studentFeePaid = Boolean.TRUE.equals(studentFeePaid)
                    && schoolVerificationStatus == SchoolVerificationStatus.VERIFIED && schoolVerifiedAt != null;
        }

        public MeResponse(UUID userUuid, String loginId, String email, String ssoRole, String status,
                          String name, String phone, String studentNo, String department, Integer grade,
                          String enrollment, LocalDate birthDate, LocalDateTime createdAt,
                          LocalDateTime updatedAt, Set<FestivalRole> festivalRoles,
                          SchoolVerificationStatus schoolVerificationStatus, Instant schoolVerifiedAt) {
            this(userUuid, loginId, email, ssoRole, status, name, phone, studentNo, department, grade,
                    enrollment, birthDate, createdAt, updatedAt, festivalRoles, schoolVerificationStatus, schoolVerifiedAt, false);
        }

        public MeResponse(UUID userUuid, String loginId, String email, String ssoRole, String status,
                          String name, String phone, String studentNo, String department, Integer grade,
                          String enrollment, LocalDate birthDate, LocalDateTime createdAt,
                          LocalDateTime updatedAt, Set<FestivalRole> festivalRoles) {
            this(userUuid, loginId, email, ssoRole, status, name, phone, studentNo, department, grade,
                    enrollment, birthDate, createdAt, updatedAt, festivalRoles,
                    SchoolVerificationStatus.UNVERIFIED, null);
        }

        public MeResponse withFestivalProfile(FestivalUserService.UserFestivalProfile profile) {
            return new MeResponse(userUuid, loginId, email, ssoRole, status, name, phone,
                    studentNo, department, grade, enrollment, birthDate, createdAt, updatedAt,
                    profile.roles(), profile.schoolVerificationStatus(), profile.schoolVerifiedAt(), profile.studentFeePaid());
        }

        @com.fasterxml.jackson.annotation.JsonProperty("schoolVerified")
        public boolean schoolVerified() {
            return schoolVerificationStatus == SchoolVerificationStatus.VERIFIED && schoolVerifiedAt != null;
        }
    }

    public record ProfileUpdateRequest(
            @Pattern(regexp = "^[0-9]{9,15}$", message = "전화번호는 숫자 9~15자리여야 합니다.") String phone,
            @Pattern(regexp = ".*\\S.*", message = "학과는 공백일 수 없습니다.")
            @Size(max = 100) String department,
            @Min(1) @Max(6) Integer grade,
            Enrollment enrollment) {
        public boolean isEmpty() {
            return phone == null && department == null && grade == null && enrollment == null;
        }
    }

    public enum Enrollment { ENROLLED, LEAVE }

    public record EmailRequest(@NotBlank @Email String email) { }

    public record EmailCodeRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 4, max = 12) String code) { }

    public record PasswordChangeRequest(
            @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(min = 8, max = 128) String newPassword) { }
}
