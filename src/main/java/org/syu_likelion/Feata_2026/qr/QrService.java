package org.syu_likelion.Feata_2026.qr;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.syu_likelion.Feata_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Feata_2026.error.ApiException;
import org.syu_likelion.Feata_2026.qr.QrDtos.QrTokenResponse;
import org.syu_likelion.Feata_2026.qr.QrDtos.QrUserView;
import org.syu_likelion.Feata_2026.sso.SsoInternalProfileClient;
import org.syu_likelion.Feata_2026.sso.SsoProfiles.InternalUserProfile;
import org.syu_likelion.Feata_2026.user.FestivalRole;
import org.syu_likelion.Feata_2026.user.FestivalUserService;
import org.syu_likelion.Feata_2026.user.UserDtos.MeResponse;
import org.syu_likelion.Feata_2026.user.UserService;

@Service
public class QrService {
    private final UserService users;
    private final FestivalUserService festivalUsers;
    private final SsoInternalProfileClient profiles;
    private final QrTokenStore tokenStore;
    private final QrProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public QrService(UserService users, FestivalUserService festivalUsers,
                     SsoInternalProfileClient profiles, QrTokenStore tokenStore,
                     QrProperties properties, Clock clock) {
        this.users = users;
        this.festivalUsers = festivalUsers;
        this.profiles = profiles;
        this.tokenStore = tokenStore;
        this.properties = properties;
        this.clock = clock;
    }

    public AuthorizedResult<QrTokenResponse> issue(String accessToken, String refreshToken) {
        AuthorizedResult<MeResponse> authenticated = users.getMe(accessToken, refreshToken);
        String token = newToken();
        tokenStore.save(token, authenticated.body().userUuid(), properties.tokenTtl());
        Instant expiresAt = clock.instant().plus(properties.tokenTtl());
        return new AuthorizedResult<>(new QrTokenResponse(token, expiresAt),
                authenticated.newAccessToken(), authenticated.newRefreshToken());
    }

    public AuthorizedResult<QrUserView> scan(String accessToken, String refreshToken, String qrToken) {
        AuthorizedResult<MeResponse> authenticated = users.getMe(accessToken, refreshToken);
        FestivalRole viewerRole = highestViewerRole(authenticated.body().festivalRoles());
        UUID targetUserUuid = tokenStore.findUserUuid(qrToken)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "QR_INVALID_OR_EXPIRED",
                        "QR이 유효하지 않거나 만료되었습니다."));
        InternalUserProfile profile = profiles.getProfile(targetUserUuid);
        QrUserView view = toView(viewerRole, profile);
        return new AuthorizedResult<>(view, authenticated.newAccessToken(), authenticated.newRefreshToken());
    }

    private FestivalRole highestViewerRole(Set<FestivalRole> roles) {
        if (roles != null && roles.contains(FestivalRole.SUPER_ADMIN)) return FestivalRole.SUPER_ADMIN;
        if (roles != null && roles.contains(FestivalRole.ADMIN)) return FestivalRole.ADMIN;
        if (roles != null && roles.contains(FestivalRole.STAFF)) return FestivalRole.STAFF;
        if (roles != null && roles.contains(FestivalRole.BOOTH_MANAGER)) return FestivalRole.BOOTH_MANAGER;
        throw new ApiException(HttpStatus.FORBIDDEN, "QR_SCAN_FORBIDDEN", "QR 사용자 조회 권한이 없습니다.");
    }

    private QrUserView toView(FestivalRole viewerRole, InternalUserProfile profile) {
        return switch (viewerRole) {
            case BOOTH_MANAGER -> new QrUserView(viewerRole, null, null, null, null, null,
                    maskName(profile.name()), null, maskStudentNo(profile.studentNo()),
                    profile.department(), profile.grade(), null, null, null, null, null);
            case STAFF -> new QrUserView(viewerRole, null, null, null, null, null,
                    profile.name(), null, profile.studentNo(), profile.department(), profile.grade(),
                    null, null, null, null, null);
            case ADMIN -> new QrUserView(viewerRole, null, null, profile.email(), null, null,
                    profile.name(), profile.phone(), profile.studentNo(), profile.department(), profile.grade(),
                    null, null, null, null, null);
            case SUPER_ADMIN -> new QrUserView(viewerRole, profile.userUuid(), profile.loginId(), profile.email(),
                    profile.ssoRole(), profile.status(), profile.name(), profile.phone(), profile.studentNo(),
                    profile.department(), profile.grade(), profile.enrollment(), profile.birthDate(),
                    profile.createdAt(), profile.updatedAt(), festivalUsers.getRoles(profile.userUuid()));
            case USER -> throw new ApiException(HttpStatus.FORBIDDEN, "QR_SCAN_FORBIDDEN",
                    "QR 사용자 조회 권한이 없습니다.");
        };
    }

    static String maskName(String name) {
        if (name == null || name.isBlank()) return name;
        int[] codePoints = name.codePoints().toArray();
        if (codePoints.length == 1) return "*";
        String first = new String(codePoints, 0, 1);
        if (codePoints.length == 2) return first + "*";
        String last = new String(codePoints, codePoints.length - 1, 1);
        return first + "*".repeat(codePoints.length - 2) + last;
    }

    static String maskStudentNo(String studentNo) {
        if (studentNo == null || studentNo.isBlank()) return studentNo;
        int visible = Math.min(4, studentNo.length());
        return studentNo.substring(0, visible) + "*".repeat(studentNo.length() - visible);
    }

    private String newToken() {
        byte[] random = new byte[32];
        secureRandom.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }
}
