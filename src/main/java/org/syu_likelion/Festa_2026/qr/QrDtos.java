package org.syu_likelion.Festa_2026.qr;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Set;
import java.util.List;
import java.util.UUID;
import org.syu_likelion.Festa_2026.user.FestivalRole;

public final class QrDtos {
    private QrDtos() { }

    public record QrTokenResponse(String token, Instant expiresAt) { }

    public record QrScanRequest(@NotBlank @Size(max = 128) String token) { }
    public record UserSearchRequest(@NotBlank @Size(min = 2, max = 100) String query) { }
    public record UserSearchResponse(List<QrUserView> items, int totalMatches, boolean truncated) { }
    public record UserRoleUpdateRequest(@NotNull FestivalRole managementRole) { }
    public record UserRoleUpdateResponse(UUID userUuid, Set<FestivalRole> festivalRoles) { }

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
            Set<FestivalRole> festivalRoles) {
        public FestivalRole managementRole() {
            if (festivalRoles == null) return null;
            if (festivalRoles.contains(FestivalRole.SUPER_ADMIN)) return FestivalRole.SUPER_ADMIN;
            if (festivalRoles.contains(FestivalRole.ADMIN)) return FestivalRole.ADMIN;
            if (festivalRoles.contains(FestivalRole.STAFF)) return FestivalRole.STAFF;
            return festivalRoles.contains(FestivalRole.USER) ? FestivalRole.USER : null;
        }
    }
}
