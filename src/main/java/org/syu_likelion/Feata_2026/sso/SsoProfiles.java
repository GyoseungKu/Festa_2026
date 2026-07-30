package org.syu_likelion.Feata_2026.sso;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

public final class SsoProfiles {
    private SsoProfiles() { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InternalUserProfile(
            @JsonAlias({"user_uuid", "userId", "user_id"}) UUID userUuid,
            @JsonAlias("login_id") String loginId,
            String email,
            @JsonAlias("sso_role") String ssoRole,
            String status,
            String name,
            String phone,
            @JsonAlias("student_no") String studentNo,
            String department,
            Integer grade,
            String enrollment,
            @JsonAlias("birth_date") String birthDate,
            @JsonAlias("created_at") String createdAt,
            @JsonAlias("updated_at") String updatedAt) { }
}
