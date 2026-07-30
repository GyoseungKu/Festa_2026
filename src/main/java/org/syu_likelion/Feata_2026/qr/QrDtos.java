package org.syu_likelion.Feata_2026.qr;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.syu_likelion.Feata_2026.user.FestivalRole;

public final class QrDtos {
    private QrDtos() { }

    public record QrTokenResponse(String token, Instant expiresAt) { }

    public record QrScanRequest(@NotBlank @Size(max = 128) String token) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
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
            Set<FestivalRole> festivalRoles) { }
}
