package org.syu_likelion.Festa_2026.admin;

import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.user.FestivalRole;
import org.syu_likelion.Festa_2026.user.UserDtos.MeResponse;
import org.syu_likelion.Festa_2026.user.UserService;

@Service
public class AdminAccessService {
    // Swagger 전용 허용 목록. 일반 회원(MEMBER에 해당)의 실제 권한명은 USER입니다.
    // 접근을 막을 권한의 줄을 주석 처리하세요. 관리자 업무 화면의 권한에는 영향이 없습니다.
    private static final Set<FestivalRole> SWAGGER_ALLOWED_ROLES = Set.of(
            FestivalRole.USER,
            FestivalRole.BOOTH_MANAGER,
            FestivalRole.STAFF,
            FestivalRole.ADMIN,
            FestivalRole.SUPER_ADMIN
    );
    private final UserService users;

    public AdminAccessService(UserService users) {
        this.users = users;
    }

    public AuthorizedResult<AdminIdentity> authenticate(String accessToken, String refreshToken) {
        return authenticate(accessToken, refreshToken, false);
    }

    public AuthorizedResult<AdminIdentity> authenticateForSwagger(String accessToken, String refreshToken) {
        return authenticate(accessToken, refreshToken, true);
    }

    private AuthorizedResult<AdminIdentity> authenticate(String accessToken, String refreshToken, boolean swagger) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "ADMIN_LOGIN_REQUIRED", "관리자 로그인이 필요합니다.");
        }
        AuthorizedResult<MeResponse> authenticated = users.getMe(accessToken, refreshToken);
        Set<FestivalRole> roles = authenticated.body().festivalRoles();
        FestivalRole role;
        if (swagger) {
            if (roles == null || roles.stream().noneMatch(SWAGGER_ALLOWED_ROLES::contains)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "SWAGGER_ROLE_REQUIRED", "API 문서 접근 권한이 없습니다.");
            }
            role = roles.stream().sorted().findFirst().orElseThrow();
        } else {
            role = highestAdminRole(roles);
        }
        MeResponse me = authenticated.body();
        String displayName = me.name() == null || me.name().isBlank() ? me.loginId() : me.name();
        AdminIdentity identity = new AdminIdentity(me.userUuid(), displayName, role, me.festivalRoles());
        return new AuthorizedResult<>(identity, authenticated.newAccessToken(), authenticated.newRefreshToken());
    }

    private FestivalRole highestAdminRole(Set<FestivalRole> roles) {
        if (roles != null && roles.contains(FestivalRole.SUPER_ADMIN)) return FestivalRole.SUPER_ADMIN;
        if (roles != null && roles.contains(FestivalRole.ADMIN)) return FestivalRole.ADMIN;
        if (roles != null && roles.contains(FestivalRole.STAFF)) return FestivalRole.STAFF;
        if (roles != null && roles.contains(FestivalRole.BOOTH_MANAGER)) return FestivalRole.BOOTH_MANAGER;
        throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_ROLE_REQUIRED", "관리자 페이지 접근 권한이 없습니다.");
    }

    public record AdminIdentity(UUID userUuid, String displayName, FestivalRole role, Set<FestivalRole> roles) {
        public AdminIdentity(UUID userUuid, String displayName, FestivalRole role) {
            this(userUuid, displayName, role, Set.of(role));
        }

        public AdminIdentity {
            roles = roles == null ? Set.of(role) : Set.copyOf(roles);
        }

        public boolean hasRole(FestivalRole required) {
            return roles.contains(required);
        }
    }
}
