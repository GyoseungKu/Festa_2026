package org.syu_likelion.Festa_2026.bamboo;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.bamboo.BambooDtos.BambooAdminPageResponse;
import org.syu_likelion.Festa_2026.bamboo.BambooDtos.BambooAuthorResponse;
import org.syu_likelion.Festa_2026.bamboo.BambooDtos.BambooMuteResponse;
import org.syu_likelion.Festa_2026.bamboo.BambooDtos.BambooNicknameResponse;
import org.syu_likelion.Festa_2026.bamboo.BambooDtos.BambooSettingsResponse;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.sso.SsoInternalProfileClient;
import org.syu_likelion.Festa_2026.sso.SsoProfiles.InternalUserProfile;
import org.syu_likelion.Festa_2026.user.FestivalRole;
import org.syu_likelion.Festa_2026.user.UserDtos.MeResponse;
import org.syu_likelion.Festa_2026.user.UserService;

/**
 * 대나무숲 관리 기능. 권한 확인이 필요하므로 인증 캐시를 쓰지 않고 매번 SSO 로 사용자를 확인한다.
 *
 * <p>{@code ...As(roles, ...)} 변형은 Thymeleaf 관리자 페이지가 이미 인증을 마친 뒤 호출한다.
 */
@Service
public class BambooAdminService {
    private static final Logger log = LoggerFactory.getLogger(BambooAdminService.class);

    private final BambooService bamboo;
    private final UserService users;
    private final SsoInternalProfileClient profiles;

    public BambooAdminService(BambooService bamboo, UserService users, SsoInternalProfileClient profiles) {
        this.bamboo = bamboo;
        this.users = users;
        this.profiles = profiles;
    }

    // ------------------------------------------------------------------ STAFF 이상

    public AuthorizedResult<BambooAdminPageResponse> reports(String access, String refresh,
                                                             int page, int size) {
        AuthorizedResult<MeResponse> authenticated = authenticate(access, refresh, FestivalRole.STAFF);
        return rotated(authenticated, bamboo.reportedMessages(page, size));
    }

    public AuthorizedResult<Integer> changeStatus(String access, String refresh, List<Long> ids,
                                                   BambooMessageStatus status) {
        AuthorizedResult<MeResponse> authenticated = authenticate(access, refresh, FestivalRole.STAFF);
        return rotated(authenticated, bamboo.changeStatus(ids, status, authenticated.body().userUuid()));
    }

    public AuthorizedResult<BambooMuteResponse> muteAuthor(String access, String refresh,
                                                            Long messageId, int minutes) {
        AuthorizedResult<MeResponse> authenticated = authenticate(access, refresh, FestivalRole.STAFF);
        return rotated(authenticated, bamboo.muteAuthorOf(messageId, minutes));
    }

    public AuthorizedResult<BambooNicknameResponse> renameAuthor(String access, String refresh,
                                                                  Long messageId, String nickname) {
        AuthorizedResult<MeResponse> authenticated = authenticate(access, refresh, FestivalRole.STAFF);
        return rotated(authenticated, bamboo.renameAuthorOf(messageId, nickname));
    }

    public AuthorizedResult<BambooSettingsResponse> settings(String access, String refresh) {
        AuthorizedResult<MeResponse> authenticated = authenticate(access, refresh, FestivalRole.STAFF);
        return rotated(authenticated, bamboo.settingsView());
    }

    // ------------------------------------------------------------------ ADMIN 이상

    public AuthorizedResult<BambooSettingsResponse> updateSettings(String access, String refresh,
                                                                    Boolean enabled, Boolean readOnly,
                                                                    Instant closesAt, Boolean clearClosesAt) {
        AuthorizedResult<MeResponse> authenticated = authenticate(access, refresh, FestivalRole.ADMIN);
        return rotated(authenticated, bamboo.updateSettings(enabled, readOnly, closesAt,
                Boolean.TRUE.equals(clearClosesAt)));
    }

    // ------------------------------------------------------------------ SUPER_ADMIN 전용

    /**
     * 익명 작성자의 신원을 확인한다. 익명 투표와 같은 정책으로 {@code SUPER_ADMIN} 에게만 제공하고,
     * 누가 언제 누구를 조회했는지 감사 로그에 남긴다. 토큰과 개인정보 자체는 기록하지 않는다.
     */
    public AuthorizedResult<BambooAuthorResponse> author(String access, String refresh, Long messageId) {
        AuthorizedResult<MeResponse> authenticated = authenticate(access, refresh, FestivalRole.SUPER_ADMIN);
        UUID actorUuid = authenticated.body().userUuid();
        UUID targetUuid = null;
        try {
            targetUuid = bamboo.authorUuidOf(messageId);
            InternalUserProfile profile = profiles.getProfile(targetUuid);
            audit(actorUuid, targetUuid, messageId, true);
            return rotated(authenticated, new BambooAuthorResponse(profile.userUuid(),
                    bamboo.nicknameOf(targetUuid), profile.loginId(), profile.email(), profile.name(),
                    profile.phone(), profile.studentNo(), profile.department(), profile.grade(),
                    profile.enrollment()));
        } catch (RuntimeException failure) {
            audit(actorUuid, targetUuid, messageId, false);
            throw failure;
        }
    }

    /**
     * Thymeleaf 관리자 페이지 전용. 화면은 {@code AdminAccessService} 로 이미 인증을 마쳤으므로
     * SSO 를 다시 호출하지 않고 역할만 확인한다. 감사 로그는 동일하게 남긴다.
     */
    public BambooAuthorResponse authorAs(FestivalRole role, UUID actorUuid, Long messageId) {
        requireRole(Set.of(role), FestivalRole.SUPER_ADMIN);
        UUID targetUuid = null;
        try {
            targetUuid = bamboo.authorUuidOf(messageId);
            InternalUserProfile profile = profiles.getProfile(targetUuid);
            audit(actorUuid, targetUuid, messageId, true);
            return new BambooAuthorResponse(profile.userUuid(), bamboo.nicknameOf(targetUuid),
                    profile.loginId(), profile.email(), profile.name(), profile.phone(),
                    profile.studentNo(), profile.department(), profile.grade(), profile.enrollment());
        } catch (RuntimeException failure) {
            audit(actorUuid, targetUuid, messageId, false);
            throw failure;
        }
    }

    private void audit(UUID actorUuid, UUID targetUuid, Long messageId, boolean success) {
        log.info("bamboo author lookup actorUuid={} actorRole={} targetUuid={} messageId={} success={} requestId={}",
                actorUuid, FestivalRole.SUPER_ADMIN, targetUuid, messageId, success, MDC.get("requestId"));
    }

    // ------------------------------------------------------------------ 공통

    private AuthorizedResult<MeResponse> authenticate(String access, String refresh, FestivalRole required) {
        AuthorizedResult<MeResponse> authenticated = users.getMe(access, refresh);
        requireRole(authenticated.body().festivalRoles(), required);
        return authenticated;
    }

    static void requireRole(Set<FestivalRole> roles, FestivalRole required) {
        if (roles == null) throw forbidden(required);
        boolean allowed = switch (required) {
            case SUPER_ADMIN -> roles.contains(FestivalRole.SUPER_ADMIN);
            case ADMIN -> roles.contains(FestivalRole.SUPER_ADMIN) || roles.contains(FestivalRole.ADMIN);
            default -> roles.contains(FestivalRole.SUPER_ADMIN) || roles.contains(FestivalRole.ADMIN)
                    || roles.contains(FestivalRole.STAFF);
        };
        if (!allowed) throw forbidden(required);
    }

    private static ApiException forbidden(FestivalRole required) {
        return new ApiException(HttpStatus.FORBIDDEN, "BAMBOO_MANAGE_FORBIDDEN",
                "대나무숲 관리 기능은 " + required + " 이상만 사용할 수 있습니다.");
    }

    private <T> AuthorizedResult<T> rotated(AuthorizedResult<MeResponse> authenticated, T body) {
        return new AuthorizedResult<>(body, authenticated.newAccessToken(), authenticated.newRefreshToken());
    }
}
