package org.syu_likelion.Festa_2026.user;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
            Set<FestivalRole> festivalRoles) {

        public MeResponse withFestivalRoles(Set<FestivalRole> roles) {
            return new MeResponse(userUuid, loginId, email, ssoRole, status, name, phone,
                    studentNo, department, grade, enrollment, birthDate, createdAt, updatedAt, roles);
        }
    }

    public record ProfileUpdateRequest(
            @Size(max = 100) String name,
            @Pattern(regexp = "^$|^[0-9]{10,11}$", message = "전화번호는 숫자 10~11자리여야 합니다.") String phone) { }

    public record EmailRequest(@NotBlank @Email String email) { }

    public record EmailCodeRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 4, max = 12) String code) { }

    public record PasswordChangeRequest(
            @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(min = 8, max = 128) String newPassword) { }
}
