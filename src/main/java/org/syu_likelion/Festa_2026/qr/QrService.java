package org.syu_likelion.Festa_2026.qr;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.qr.QrDtos.QrTokenResponse;
import org.syu_likelion.Festa_2026.qr.QrDtos.QrUserView;
import org.syu_likelion.Festa_2026.qr.QrDtos.UserSearchResponse;
import org.syu_likelion.Festa_2026.qr.QrDtos.UserRoleUpdateResponse;
import org.syu_likelion.Festa_2026.qr.QrDtos.UserSchoolVerificationUpdateResponse;
import org.syu_likelion.Festa_2026.sso.SsoInternalProfileClient;
import org.syu_likelion.Festa_2026.sso.SsoProfiles.InternalUserProfile;
import org.syu_likelion.Festa_2026.user.FestivalRole;
import org.syu_likelion.Festa_2026.user.FestivalUserService;
import org.syu_likelion.Festa_2026.user.UserDtos.MeResponse;
import org.syu_likelion.Festa_2026.user.UserService;

@Service
public class QrService {
    private static final int PROFILE_BATCH_SIZE = 100;
    private static final int DEFAULT_SEARCH_PAGE_SIZE = 20;
    private final UserService users;
    private final FestivalUserService festivalUsers;
    private final SsoInternalProfileClient profiles;
    private final QrTokenStore tokenStore;
    private final QrProperties properties;
    private final Clock clock;
    private final org.syu_likelion.Festa_2026.wristband.WristbandService wristbands;
    private final SecureRandom secureRandom = new SecureRandom();

    public QrService(UserService users, FestivalUserService festivalUsers,
                     SsoInternalProfileClient profiles, QrTokenStore tokenStore,
                     QrProperties properties, Clock clock,
                     org.syu_likelion.Festa_2026.wristband.WristbandService wristbands) {
        this.users = users;
        this.festivalUsers = festivalUsers;
        this.profiles = profiles;
        this.tokenStore = tokenStore;
        this.properties = properties;
        this.clock = clock;
        this.wristbands = wristbands;
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
        QrUserView view = scanAs(viewerRole, qrToken);
        return new AuthorizedResult<>(view, authenticated.newAccessToken(), authenticated.newRefreshToken());
    }

    public QrUserView scanAs(FestivalRole viewerRole, String qrToken) {
        if (viewerRole == null || viewerRole == FestivalRole.USER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "QR_SCAN_FORBIDDEN", "QR 사용자 조회 권한이 없습니다.");
        }
        UUID targetUserUuid = resolveUserUuid(qrToken);
        InternalUserProfile profile = profiles.getProfile(targetUserUuid);
        return toView(viewerRole, profile);
    }

    public AuthorizedResult<UserSearchResponse> search(String accessToken, String refreshToken, String query,
                                                         Integer page, Integer size) {
        AuthorizedResult<MeResponse> authenticated = users.getMe(accessToken, refreshToken);
        FestivalRole viewerRole = highestSearchRole(authenticated.body().festivalRoles());
        return new AuthorizedResult<>(searchAs(viewerRole, query, page == null ? 0 : page,
                size == null ? DEFAULT_SEARCH_PAGE_SIZE : size), authenticated.newAccessToken(),
                authenticated.newRefreshToken());
    }

    public UserSearchResponse searchAs(FestivalRole viewerRole, String query) {
        return searchAs(viewerRole, query, 0, DEFAULT_SEARCH_PAGE_SIZE);
    }

    public UserSearchResponse searchAs(FestivalRole viewerRole, String query, int page, int size) {
        requireSearchRole(viewerRole);
        String term = query == null ? "" : query.trim();
        String normalized = normalize(term);
        if (normalized.length() < 2 || term.length() > 100)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_USER_SEARCH_QUERY",
                    "검색어는 공백 제외 2자 이상 100자 이하로 입력해 주세요.");
        String digits = digits(term);
        List<InternalUserProfile> matched = new ArrayList<>();
        List<UUID> ids = festivalUsers.getLinkedUserUuids();
        for (int start = 0; start < ids.size(); start += PROFILE_BATCH_SIZE) {
            List<UUID> batch = ids.subList(start, Math.min(start + PROFILE_BATCH_SIZE, ids.size()));
            profiles.getProfiles(batch).stream().filter(profile -> matches(profile, normalized, digits))
                    .forEach(matched::add);
        }
        matched.sort(Comparator.comparing((InternalUserProfile profile) -> !exactMatch(profile, normalized, digits))
                .thenComparing(profile -> normalize(profile.name()), Comparator.nullsLast(String::compareTo))
                .thenComparing(profile -> normalize(profile.studentNo()), Comparator.nullsLast(String::compareTo)));
        int safeSize = Math.max(1, Math.min(size, 100));
        int total = matched.size();
        int totalPages = total == 0 ? 0 : (total + safeSize - 1) / safeSize;
        int safePage = totalPages == 0 ? 0 : Math.min(Math.max(0, page), totalPages - 1);
        int from = (int) Math.min(total, (long) safePage * safeSize);
        int to = Math.min(total, from + safeSize);
        List<QrUserView> items = matched.subList(from, to).stream().map(profile -> toView(viewerRole, profile)).toList();
        return new UserSearchResponse(items, safePage, safeSize, total, totalPages);
    }

    public AuthorizedResult<UserRoleUpdateResponse> updateRole(String accessToken, String refreshToken,
            UUID targetUuid, FestivalRole requestedRole) {
        AuthorizedResult<MeResponse> authenticated = users.getMe(accessToken, refreshToken);
        FestivalRole actorRole = highestViewerRole(authenticated.body().festivalRoles());
        UserRoleUpdateResponse response = updateRoleAs(authenticated.body().userUuid(), actorRole,
                targetUuid, requestedRole);
        return new AuthorizedResult<>(response, authenticated.newAccessToken(), authenticated.newRefreshToken());
    }

    public UserRoleUpdateResponse updateRoleAs(UUID actorUuid, FestivalRole actorRole,
                                                UUID targetUuid, FestivalRole requestedRole) {
        Set<FestivalRole> roles = festivalUsers.updateManagementRole(actorUuid, actorRole, targetUuid, requestedRole);
        return new UserRoleUpdateResponse(targetUuid, roles);
    }

    public AuthorizedResult<UserSchoolVerificationUpdateResponse> updateSchoolVerification(
            String accessToken, String refreshToken, UUID targetUuid, boolean verified) {
        AuthorizedResult<MeResponse> authenticated = users.getMe(accessToken, refreshToken);
        FestivalRole actorRole = highestViewerRole(authenticated.body().festivalRoles());
        UserSchoolVerificationUpdateResponse response = updateSchoolVerificationAs(actorRole, targetUuid, verified);
        return new AuthorizedResult<>(response, authenticated.newAccessToken(), authenticated.newRefreshToken());
    }

    public UserSchoolVerificationUpdateResponse updateSchoolVerificationAs(
            FestivalRole actorRole, UUID targetUuid, boolean verified) {
        FestivalUserService.UserFestivalProfile profile =
                festivalUsers.updateSchoolVerificationByAdmin(actorRole, targetUuid, verified);
        return new UserSchoolVerificationUpdateResponse(targetUuid, profile.schoolVerificationStatus(),
                profile.schoolVerified(), profile.schoolVerifiedAt(), profile.studentFeePaid());
    }

    public UUID resolveUserUuid(String qrToken) {
        if (qrToken == null || qrToken.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "QR_INVALID_OR_EXPIRED",
                    "QR이 유효하지 않거나 만료되었습니다.");
        }
        return tokenStore.findUserUuid(qrToken.trim())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "QR_INVALID_OR_EXPIRED",
                        "QR이 유효하지 않거나 만료되었습니다."));
    }

    private FestivalRole highestViewerRole(Set<FestivalRole> roles) {
        if (roles != null && roles.contains(FestivalRole.SUPER_ADMIN)) return FestivalRole.SUPER_ADMIN;
        if (roles != null && roles.contains(FestivalRole.ADMIN)) return FestivalRole.ADMIN;
        if (roles != null && roles.contains(FestivalRole.STAFF)) return FestivalRole.STAFF;
        if (roles != null && roles.contains(FestivalRole.BOOTH_MANAGER)) return FestivalRole.BOOTH_MANAGER;
        throw new ApiException(HttpStatus.FORBIDDEN, "QR_SCAN_FORBIDDEN", "QR 사용자 조회 권한이 없습니다.");
    }

    private FestivalRole highestSearchRole(Set<FestivalRole> roles) {
        FestivalRole role = highestViewerRole(roles);
        requireSearchRole(role);
        return role;
    }

    private void requireSearchRole(FestivalRole role) {
        if (role != FestivalRole.STAFF && role != FestivalRole.ADMIN && role != FestivalRole.SUPER_ADMIN)
            throw new ApiException(HttpStatus.FORBIDDEN, "USER_SEARCH_FORBIDDEN",
                    "사용자 검색은 STAFF 이상만 사용할 수 있습니다.");
    }

    private QrUserView toView(FestivalRole viewerRole, InternalUserProfile profile) {
        FestivalUserService.UserFestivalProfile festivalProfile = festivalProfile(profile.userUuid());
        return toView(viewerRole, profile, festivalProfile);
    }

    QrUserView toView(FestivalRole viewerRole, InternalUserProfile profile,
                      FestivalUserService.UserFestivalProfile festivalProfile) {
        return toView(viewerRole, profile, festivalProfile,
                viewerRole == FestivalRole.BOOTH_MANAGER ? null : wristbands.mine(profile.userUuid()));
    }

    QrUserView toView(FestivalRole viewerRole, InternalUserProfile profile,
                      FestivalUserService.UserFestivalProfile festivalProfile,
                      org.syu_likelion.Festa_2026.wristband.WristbandService.MyStatus wristband) {
        QrUserView view = switch (viewerRole) {
            case BOOTH_MANAGER -> new QrUserView(viewerRole, null, null, null, null, null,
                    maskName(profile.name()), null, maskStudentNo(profile.studentNo()),
                    profile.department(), profile.grade(), null, null, null, null, null,
                    festivalProfile.schoolVerificationStatus(), festivalProfile.schoolVerifiedAt(), festivalProfile.studentFeePaid());
            case STAFF -> new QrUserView(viewerRole, null, null, null, null, null,
                    maskName(profile.name()), null, maskStudentNo(profile.studentNo()), profile.department(), profile.grade(),
                    null, null, null, null, null,
                    festivalProfile.schoolVerificationStatus(), festivalProfile.schoolVerifiedAt(), festivalProfile.studentFeePaid());
            case ADMIN -> new QrUserView(viewerRole, profile.userUuid(), null, profile.email(), null, null,
                    profile.name(), profile.phone(), profile.studentNo(), profile.department(), profile.grade(),
                    null, null, null, null, festivalProfile.roles(),
                    festivalProfile.schoolVerificationStatus(), festivalProfile.schoolVerifiedAt(), festivalProfile.studentFeePaid());
            case SUPER_ADMIN -> new QrUserView(viewerRole, profile.userUuid(), profile.loginId(), profile.email(),
                    profile.ssoRole(), profile.status(), profile.name(), profile.phone(), profile.studentNo(),
                    profile.department(), profile.grade(), profile.enrollment(), profile.birthDate(),
                    profile.createdAt(), profile.updatedAt(), festivalProfile.roles(),
                    festivalProfile.schoolVerificationStatus(), festivalProfile.schoolVerifiedAt(), festivalProfile.studentFeePaid());
            case USER -> throw new ApiException(HttpStatus.FORBIDDEN, "QR_SCAN_FORBIDDEN",
                    "QR 사용자 조회 권한이 없습니다.");
        };
        // The HTML-only status is computed before UUID masking and never serialized by the QR API.
        return viewerRole == FestivalRole.BOOTH_MANAGER ? view : view.withWristband(wristband);
    }

    private FestivalUserService.UserFestivalProfile festivalProfile(UUID userUuid) {
        FestivalUserService.UserFestivalProfile profile = festivalUsers.getProfile(userUuid);
        return profile != null ? profile : new FestivalUserService.UserFestivalProfile(
                festivalUsers.getRoles(userUuid),
                org.syu_likelion.Festa_2026.user.SchoolVerificationStatus.UNVERIFIED, null);
    }

    private boolean matches(InternalUserProfile profile, String term, String digitTerm) {
        if (contains(profile.name(), term) || contains(profile.loginId(), term)
                || contains(profile.email(), term) || contains(profile.department(), term)
                || contains(profile.enrollment(), term) || contains(profile.userUuid().toString(), term)) return true;
        return !digitTerm.isEmpty() && (digits(profile.studentNo()).contains(digitTerm)
                || digits(profile.phone()).contains(digitTerm));
    }

    private boolean exactMatch(InternalUserProfile profile, String term, String digitTerm) {
        if (equalsNormalized(profile.name(), term) || equalsNormalized(profile.loginId(), term)
                || equalsNormalized(profile.email(), term) || equalsNormalized(profile.userUuid().toString(), term)) return true;
        return !digitTerm.isEmpty() && (digits(profile.studentNo()).equals(digitTerm)
                || digits(profile.phone()).equals(digitTerm));
    }

    private boolean contains(String value, String term) {
        return value != null && normalize(value).contains(term);
    }
    private boolean equalsNormalized(String value, String term) {
        return value != null && normalize(value).equals(term);
    }
    private String normalize(String value) {
        return value == null ? null : value.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
    private String digits(String value) { return value == null ? "" : value.replaceAll("\\D", ""); }

    public static String maskName(String name) {
        if (org.syu_likelion.Festa_2026.user.DeletedUserIdentity.NAME.equals(name)) return name;
        if (name == null || name.isBlank()) return name;
        int[] codePoints = name.codePoints().toArray();
        if (codePoints.length == 1) return "*";
        String first = new String(codePoints, 0, 1);
        if (codePoints.length == 2) return first + "*";
        String last = new String(codePoints, codePoints.length - 1, 1);
        return first + "*".repeat(codePoints.length - 2) + last;
    }

    public static String maskStudentNo(String studentNo) {
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
