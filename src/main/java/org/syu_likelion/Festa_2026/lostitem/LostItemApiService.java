package org.syu_likelion.Festa_2026.lostitem;

import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.lostitem.LostItemDtos.LostItemMutationRequest;
import org.syu_likelion.Festa_2026.lostitem.LostItemDtos.LostItemResponse;
import org.syu_likelion.Festa_2026.user.FestivalRole;
import org.syu_likelion.Festa_2026.user.UserDtos.MeResponse;
import org.syu_likelion.Festa_2026.user.UserService;

@Service
public class LostItemApiService {
    private final LostItemService lostItems;
    private final UserService users;

    public LostItemApiService(LostItemService lostItems, UserService users) {
        this.lostItems = lostItems;
        this.users = users;
    }

    public AuthorizedResult<LostItemResponse> create(String accessToken, String refreshToken,
                                                     LostItemMutationRequest request,
                                                     List<MultipartFile> images) {
        AuthorizedResult<MeResponse> authenticated = authenticateStaff(accessToken, refreshToken);
        MeResponse actor = authenticated.body();
        String authorName = actor.name() == null || actor.name().isBlank() ? actor.loginId() : actor.name();
        return rotated(authenticated,
                lostItems.createAs(actor.userUuid(), authorName, request, images));
    }

    public AuthorizedResult<LostItemResponse> update(Long id, String accessToken, String refreshToken,
                                                     LostItemMutationRequest request,
                                                     List<Long> removeImageIds,
                                                     List<MultipartFile> images) {
        AuthorizedResult<MeResponse> authenticated = authenticateStaff(accessToken, refreshToken);
        return rotated(authenticated, lostItems.updateAs(id, authenticated.body().userUuid(), request,
                removeImageIds, images));
    }

    public AuthorizedResult<LostItemResponse> changeStatus(Long id, String accessToken, String refreshToken,
                                                           LostItemStatus status) {
        AuthorizedResult<MeResponse> authenticated = authenticateStaff(accessToken, refreshToken);
        return rotated(authenticated,
                lostItems.changeStatusAs(id, authenticated.body().userUuid(), status));
    }

    public AuthorizedResult<LostItemResponse> changePinned(Long id, String accessToken, String refreshToken,
                                                           boolean pinned) {
        AuthorizedResult<MeResponse> authenticated = authenticateStaff(accessToken, refreshToken);
        return rotated(authenticated,
                lostItems.changePinnedAs(id, authenticated.body().userUuid(), pinned));
    }

    public AuthorizedResult<Void> delete(Long id, String accessToken, String refreshToken) {
        AuthorizedResult<MeResponse> authenticated = authenticateStaff(accessToken, refreshToken);
        lostItems.deleteAs(id);
        return new AuthorizedResult<>(null, authenticated.newAccessToken(), authenticated.newRefreshToken());
    }

    private AuthorizedResult<MeResponse> authenticateStaff(String accessToken, String refreshToken) {
        AuthorizedResult<MeResponse> authenticated = users.getMe(accessToken, refreshToken);
        requireStaff(authenticated.body().festivalRoles());
        return authenticated;
    }

    private void requireStaff(Set<FestivalRole> roles) {
        if (roles == null || (!roles.contains(FestivalRole.STAFF)
                && !roles.contains(FestivalRole.ADMIN) && !roles.contains(FestivalRole.SUPER_ADMIN))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "LOST_ITEM_MANAGE_FORBIDDEN",
                    "분실물 공지는 STAFF 이상만 관리할 수 있습니다.");
        }
    }

    private <T> AuthorizedResult<T> rotated(AuthorizedResult<MeResponse> authenticated, T body) {
        return new AuthorizedResult<>(body, authenticated.newAccessToken(), authenticated.newRefreshToken());
    }
}
