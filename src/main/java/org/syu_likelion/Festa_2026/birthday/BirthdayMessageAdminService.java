package org.syu_likelion.Festa_2026.birthday;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.birthday.BirthdayMessageDtos.AdminBirthdayMessagePageResponse;
import org.syu_likelion.Festa_2026.birthday.BirthdayMessageDtos.AdminBirthdayMessageResponse;
import org.syu_likelion.Festa_2026.birthday.BirthdayMessageDtos.AdminHeartPageResponse;
import org.syu_likelion.Festa_2026.birthday.BirthdayMessageDtos.AdminHeartUserResponse;
import org.syu_likelion.Festa_2026.birthday.BirthdayMessageDtos.AdminUserView;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.sso.SsoInternalProfileClient;
import org.syu_likelion.Festa_2026.sso.SsoProfiles.InternalUserProfile;
import org.syu_likelion.Festa_2026.user.FestivalRole;
import org.syu_likelion.Festa_2026.user.FestivalUserService;
import org.syu_likelion.Festa_2026.user.UserDtos.MeResponse;
import org.syu_likelion.Festa_2026.user.UserService;

@Service
public class BirthdayMessageAdminService {
    private final BirthdayMessageService messages;
    private final UserService users;
    private final SsoInternalProfileClient profiles;
    private final FestivalUserService festivalUsers;

    public BirthdayMessageAdminService(BirthdayMessageService messages, UserService users,
                                       SsoInternalProfileClient profiles, FestivalUserService festivalUsers) {
        this.messages = messages;
        this.users = users;
        this.profiles = profiles;
        this.festivalUsers = festivalUsers;
    }

    public AuthorizedResult<AdminBirthdayMessagePageResponse> list(String access, String refresh,
                                                                   BirthdayMessageSort sort, int page, int size) {
        AuthorizedResult<MeResponse> authenticated = authenticateStaff(access, refresh);
        return rotated(authenticated, listAs(authenticated.body().festivalRoles(), sort, page, size));
    }

    public AuthorizedResult<AdminHeartPageResponse> hearts(Long messageId, String access, String refresh,
                                                           int page, int size) {
        AuthorizedResult<MeResponse> authenticated = authenticateStaff(access, refresh);
        return rotated(authenticated, heartsAs(messageId, authenticated.body().festivalRoles(), page, size));
    }

    public AuthorizedResult<Void> delete(Long messageId, String access, String refresh) {
        AuthorizedResult<MeResponse> authenticated = authenticateStaff(access, refresh);
        messages.deleteAsAdmin(messageId);
        return rotated(authenticated, null);
    }

    public AdminBirthdayMessagePageResponse listAs(Set<FestivalRole> roles, BirthdayMessageSort sort,
                                                   int page, int size) {
        FestivalRole viewerRole = highestStaffRole(roles);
        Page<BirthdayMessage> result = messages.listEntities(sort, page, size);
        Map<UUID, InternalUserProfile> profileMap = profileMap(result.getContent().stream()
                .map(BirthdayMessage::getAuthorUuid).toList());
        List<AdminBirthdayMessageResponse> items = result.getContent().stream().map(message ->
                new AdminBirthdayMessageResponse(message.getId(), message.getContent(), message.getHeartCount(),
                        message.getCreatedAt(), toUserView(viewerRole, profileMap.get(message.getAuthorUuid()),
                        message.getAuthorUuid()), message.getDesignNo())).toList();
        return new AdminBirthdayMessagePageResponse(items, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    public AdminBirthdayMessageResponse detailAs(Long messageId, Set<FestivalRole> roles) {
        FestivalRole viewerRole = highestStaffRole(roles);
        BirthdayMessage message = messages.findEntity(messageId);
        InternalUserProfile profile = profileMap(List.of(message.getAuthorUuid()))
                .get(message.getAuthorUuid());
        return new AdminBirthdayMessageResponse(message.getId(), message.getContent(), message.getHeartCount(),
                message.getCreatedAt(), toUserView(viewerRole, profile, message.getAuthorUuid()), message.getDesignNo());
    }

    public AdminHeartPageResponse heartsAs(Long messageId, Set<FestivalRole> roles, int page, int size) {
        FestivalRole viewerRole = highestStaffRole(roles);
        BirthdayMessage message = messages.findEntity(messageId);
        Page<BirthdayMessageHeart> result = messages.listHeartEntities(messageId, page, size);
        Map<UUID, InternalUserProfile> profileMap = profileMap(result.getContent().stream()
                .map(BirthdayMessageHeart::getUserUuid).toList());
        List<AdminHeartUserResponse> items = result.getContent().stream().map(heart ->
                new AdminHeartUserResponse(toUserView(viewerRole, profileMap.get(heart.getUserUuid()),
                        heart.getUserUuid()), heart.getCreatedAt())).toList();
        return new AdminHeartPageResponse(messageId, message.getHeartCount(), items,
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    private AuthorizedResult<MeResponse> authenticateStaff(String access, String refresh) {
        AuthorizedResult<MeResponse> authenticated = users.getMe(access, refresh);
        highestStaffRole(authenticated.body().festivalRoles());
        return authenticated;
    }

    private FestivalRole highestStaffRole(Set<FestivalRole> roles) {
        if (roles != null && roles.contains(FestivalRole.SUPER_ADMIN)) return FestivalRole.SUPER_ADMIN;
        if (roles != null && roles.contains(FestivalRole.ADMIN)) return FestivalRole.ADMIN;
        if (roles != null && roles.contains(FestivalRole.STAFF)) return FestivalRole.STAFF;
        throw new ApiException(HttpStatus.FORBIDDEN, "BIRTHDAY_MESSAGE_MANAGE_FORBIDDEN",
                "생일축하 쪽지는 STAFF 이상만 관리할 수 있습니다.");
    }

    private Map<UUID, InternalUserProfile> profileMap(List<UUID> userUuids) {
        List<UUID> unique = userUuids.stream().distinct().toList();
        Map<UUID, InternalUserProfile> result = new LinkedHashMap<>();
        profiles.getProfiles(unique).forEach(profile -> result.put(profile.userUuid(), profile));
        return result;
    }

    private AdminUserView toUserView(FestivalRole viewerRole, InternalUserProfile profile, UUID fallbackUuid) {
        if (profile == null) return new AdminUserView(viewerRole,
                viewerRole == FestivalRole.SUPER_ADMIN ? fallbackUuid : null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, Set.of());
        return switch (viewerRole) {
            case STAFF -> new AdminUserView(viewerRole, null, null, null, null, null,
                    profile.name(), null, profile.studentNo(), profile.department(), profile.grade(),
                    null, null, null, null, null);
            case ADMIN -> new AdminUserView(viewerRole, null, null, profile.email(), null, null,
                    profile.name(), profile.phone(), profile.studentNo(), profile.department(), profile.grade(),
                    null, null, null, null, null);
            case SUPER_ADMIN -> new AdminUserView(viewerRole, profile.userUuid(), profile.loginId(), profile.email(),
                    profile.ssoRole(), profile.status(), profile.name(), profile.phone(), profile.studentNo(),
                    profile.department(), profile.grade(), profile.enrollment(), profile.birthDate(),
                    profile.createdAt(), profile.updatedAt(), festivalUsers.getRoles(profile.userUuid()));
            default -> throw new ApiException(HttpStatus.FORBIDDEN, "BIRTHDAY_MESSAGE_MANAGE_FORBIDDEN",
                    "생일축하 쪽지는 STAFF 이상만 관리할 수 있습니다.");
        };
    }

    private <T> AuthorizedResult<T> rotated(AuthorizedResult<MeResponse> authenticated, T body) {
        return new AuthorizedResult<>(body, authenticated.newAccessToken(), authenticated.newRefreshToken());
    }
}
