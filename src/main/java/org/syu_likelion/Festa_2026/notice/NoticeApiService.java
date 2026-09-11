package org.syu_likelion.Festa_2026.notice;

import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.notice.NoticeDtos.NoticeMutationRequest;
import org.syu_likelion.Festa_2026.notice.NoticeDtos.NoticeResponse;
import org.syu_likelion.Festa_2026.user.FestivalRole;
import org.syu_likelion.Festa_2026.user.UserDtos.MeResponse;
import org.syu_likelion.Festa_2026.user.UserService;

@Service
public class NoticeApiService {
    private final NoticeService notices;
    private final UserService users;

    public NoticeApiService(NoticeService notices, UserService users) {
        this.notices = notices;
        this.users = users;
    }

    public AuthorizedResult<NoticeResponse> create(String accessToken, String refreshToken,
                                                     NoticeMutationRequest request,
                                                     List<MultipartFile> media, List<MultipartFile> files) {
        AuthorizedResult<MeResponse> authenticated = authenticateStaff(accessToken, refreshToken);
        MeResponse actor = authenticated.body();
        String authorName = actor.name() == null || actor.name().isBlank() ? actor.loginId() : actor.name();
        return rotated(authenticated,
                notices.createAs(actor.userUuid(), authorName, request, NoticeUploads.combine(media, files)));
    }

    public AuthorizedResult<NoticeResponse> update(Long id, String accessToken, String refreshToken,
                                                     NoticeMutationRequest request,
                                                     List<Long> removeAttachmentIds,
                                                     List<MultipartFile> media, List<MultipartFile> files) {
        AuthorizedResult<MeResponse> authenticated = authenticateStaff(accessToken, refreshToken);
        return rotated(authenticated, notices.updateAs(id, authenticated.body().userUuid(), request,
                removeAttachmentIds, NoticeUploads.combine(media, files)));
    }

    public AuthorizedResult<NoticeResponse> changePinned(Long id, String accessToken, String refreshToken,
                                                           boolean pinned) {
        AuthorizedResult<MeResponse> authenticated = authenticateStaff(accessToken, refreshToken);
        return rotated(authenticated,
                notices.changePinnedAs(id, authenticated.body().userUuid(), pinned));
    }

    public AuthorizedResult<Void> delete(Long id, String accessToken, String refreshToken) {
        AuthorizedResult<MeResponse> authenticated = authenticateStaff(accessToken, refreshToken);
        notices.deleteAs(id);
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
            throw new ApiException(HttpStatus.FORBIDDEN, "NOTICE_MANAGE_FORBIDDEN",
                    "일반 공지는 STAFF 이상만 관리할 수 있습니다.");
        }
    }

    private <T> AuthorizedResult<T> rotated(AuthorizedResult<MeResponse> authenticated, T body) {
        return new AuthorizedResult<>(body, authenticated.newAccessToken(), authenticated.newRefreshToken());
    }
}
