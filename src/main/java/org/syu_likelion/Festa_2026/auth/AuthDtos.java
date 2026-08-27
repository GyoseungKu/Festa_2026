package org.syu_likelion.Festa_2026.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() { }

    public record EmailRequest(@NotBlank @Email String email) { }

    public record EmailCodeRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 4, max = 12) String code) { }

    public record SignupRequest(
            @NotBlank @Size(max = 100) String loginId,
            @NotBlank @Size(min = 8, max = 128) String password,
            @NotBlank @Email String email,
            @NotBlank @Size(max = 100) String name,
            @Pattern(regexp = "^$|^[0-9]{10,11}$", message = "전화번호는 숫자 10~11자리여야 합니다.") String phone,
            @NotBlank @Size(max = 50) String studentNo,
            @NotBlank @Size(max = 100) String department,
            Integer grade,
            @Size(max = 50) String enrollment,
            LocalDate birthDate,
            AcademicInfoSource academicInfoSource) {
        public SignupRequest(String loginId, String password, String email, String name, String phone,
                             String studentNo, String department, Integer grade,
                             String enrollment, LocalDate birthDate) {
            this(loginId, password, email, name, phone, studentNo, department, grade,
                    enrollment, birthDate, AcademicInfoSource.MANUAL);
        }

        public AcademicInfoSource effectiveAcademicInfoSource() {
            return academicInfoSource == null ? AcademicInfoSource.MANUAL : academicInfoSource;
        }

        public SignupRequest withVerifiedAcademicInfo(String verifiedName, String verifiedStudentNo,
                                                      String verifiedDepartment) {
            return new SignupRequest(loginId, password, email, verifiedName, phone,
                    verifiedStudentNo, verifiedDepartment, grade, enrollment, birthDate,
                    AcademicInfoSource.SCHOOL_SSO);
        }
    }

    public enum AcademicInfoSource { MANUAL, SCHOOL_SSO }

    public record LoginRequest(
            @NotBlank @Size(max = 100) String loginId,
            @NotBlank @Size(max = 128) String password) { }

    public enum RecoveryPurpose { FIND_ID, RESET_PASSWORD }

    public record RecoveryEmailSendRequest(
            @NotBlank @Email String email,
            @NotNull RecoveryPurpose purpose,
            @Size(max = 100) String loginId) { }

    public record FindIdVerifyRequest(
            @NotBlank @Email String email,
            @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "인증번호는 숫자 6자리여야 합니다.")
            String code) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FindIdResponse(String loginId) { }

    public record ResetPasswordRequest(
            @NotBlank @Size(max = 100) String loginId,
            @NotBlank @Email String email,
            @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "인증번호는 숫자 6자리여야 합니다.")
            String code,
            @NotBlank @Size(min = 8, max = 128) String newPassword) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TokenResponse(String accessToken) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SignupResponse(UUID userUuid) { }

    public record MessageResponse(String message) { }
}
