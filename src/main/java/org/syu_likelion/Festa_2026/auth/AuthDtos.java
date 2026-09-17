package org.syu_likelion.Festa_2026.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.CodePointLength;
import java.time.LocalDate;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() { }

    public record EmailRequest(@NotBlank @Email String email) { }

    public record EmailCodeRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 4, max = 12) String code) { }

    public record SignupRequest(
            @Schema(description = "4–50 Unicode 코드 포인트. 한글·특수문자·이모지 허용. 공백만은 금지하며 trim·소문자 변환 없이 전달합니다. 중복 확인 성공도 최종 가입을 보장하지 않습니다.", minLength = 4, maxLength = 50, example = "홍길동계정")
            @NotBlank @CodePointLength(min = 4, max = 50, message = "아이디는 4–50자여야 합니다.") String loginId,
            @Schema(description = "8–20자, 대문자·소문자·숫자·특수문자 각각 필수. U+0021–U+007E만 허용하며 공백·한글·이모지 금지. trim하지 않습니다.", requiredMode = Schema.RequiredMode.REQUIRED, minLength = 8, maxLength = 20, format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
            @SignupPassword String password,
            @NotBlank @Email String email,
            @NotBlank @Size(max = 100) String name,
            @Pattern(regexp = "^$|^[0-9]{10,11}$", message = "전화번호는 숫자 10~11자리여야 합니다.") String phone,
            @NotBlank @Size(max = 50) String studentNo,
            @NotBlank @Size(max = 100) String department,
            Integer grade,
            @Size(max = 50) String enrollment,
            LocalDate birthDate,
            @Schema(description = "생략/null이면 MANUAL. SCHOOL_SSO는 동일 브라우저의 학교 인증 세션이 필요합니다. name·studentNo·department도 필수 검증을 통과하도록 보내야 하며 이후 서버가 학교 정보로 교체합니다.", defaultValue = "MANUAL")
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
            @NotBlank @Size(max = 128) String password,
            @Schema(description = "생략/null은 false. 409 ACCOUNT_REACTIVATION_REQUIRED를 받은 뒤 사용자가 명시적으로 복구에 동의한 경우에만 true로 재요청합니다. 자동 복구 금지.", defaultValue = "false")
            Boolean reactivate) {
        public LoginRequest {
            reactivate = Boolean.TRUE.equals(reactivate);
        }
        public LoginRequest(String loginId, String password) {
            this(loginId, password, false);
        }
    }

    public enum RecoveryPurpose { FIND_ID, RESET_PASSWORD }

    public record RecoveryEmailSendRequest(
            @NotBlank @Email String email,
            @NotNull RecoveryPurpose purpose,
            @Schema(description = "RESET_PASSWORD일 때 필수. FIND_ID에서는 필요하지 않습니다.")
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
            @Schema(description = "회원가입과 동일: 8–20자 ASCII 출력 문자(U+0021–U+007E), 대문자·소문자·숫자·특수문자 각각 필수. 공백 금지, trim하지 않음.", requiredMode = Schema.RequiredMode.REQUIRED, minLength = 8, maxLength = 20, format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
            @SignupPassword String newPassword) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TokenResponse(String accessToken) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SignupResponse(UUID userUuid) { }

    public record MessageResponse(String message) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AvailabilityResponse(boolean available) { }
}
