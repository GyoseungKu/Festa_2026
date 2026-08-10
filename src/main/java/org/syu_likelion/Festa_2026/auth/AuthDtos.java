package org.syu_likelion.Festa_2026.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
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
            @Size(max = 100) String name,
            @Pattern(regexp = "^$|^[0-9]{10,11}$", message = "전화번호는 숫자 10~11자리여야 합니다.") String phone,
            @Size(max = 50) String studentNo,
            @Size(max = 100) String department,
            Integer grade,
            @Size(max = 50) String enrollment,
            LocalDate birthDate) { }

    public record LoginRequest(
            @NotBlank @Size(max = 100) String loginId,
            @NotBlank @Size(max = 128) String password) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TokenResponse(String accessToken) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SignupResponse(UUID userUuid) { }

    public record MessageResponse(String message) { }
}
