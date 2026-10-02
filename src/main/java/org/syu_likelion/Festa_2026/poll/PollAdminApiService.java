package org.syu_likelion.Festa_2026.poll;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.poll.PollDtos.PollAdminDetailResponse;
import org.syu_likelion.Festa_2026.poll.PollDtos.PollDetailResponse;
import org.syu_likelion.Festa_2026.poll.PollDtos.PollMutationRequest;
import org.syu_likelion.Festa_2026.poll.PollDtos.PollMediaOrderRequest;
import org.syu_likelion.Festa_2026.poll.PollDtos.PollSettingsRequest;
import org.syu_likelion.Festa_2026.poll.PollDtos.PollSummaryResponse;
import org.syu_likelion.Festa_2026.user.FestivalRole;
import org.syu_likelion.Festa_2026.user.UserDtos.MeResponse;
import org.syu_likelion.Festa_2026.user.UserService;

@Service
public class PollAdminApiService {
    private final PollService polls;
    private final UserService users;
    public PollAdminApiService(PollService polls, UserService users) { this.polls = polls; this.users = users; }

    public AuthorizedResult<List<PollSummaryResponse>> list(String access, String refresh) {
        Auth auth = auth(access, refresh); return rotate(auth, polls.listAdmin());
    }
    public AuthorizedResult<PollAdminDetailResponse> detail(Long id, int page, int size, String access, String refresh) {
        Auth auth = auth(access, refresh); return rotate(auth, polls.adminDetailAs(id, auth.role(), page, size));
    }
    public AuthorizedResult<PollDetailResponse> create(PollMutationRequest body, String access, String refresh) {
        Auth auth = auth(access, refresh); return rotate(auth, polls.createAs(auth.me().body().userUuid(), body));
    }
    public AuthorizedResult<PollDetailResponse> update(Long id, PollMutationRequest body, String access, String refresh) {
        Auth auth = auth(access, refresh); return rotate(auth, polls.updateDefinitionAs(id, auth.me().body().userUuid(), body));
    }
    public AuthorizedResult<PollDetailResponse> settings(Long id, PollSettingsRequest body, String access, String refresh) {
        Auth auth = auth(access, refresh); return rotate(auth, polls.updateSettingsAs(id, auth.me().body().userUuid(), body));
    }
    public AuthorizedResult<PollDetailResponse> close(Long id, String access, String refresh) {
        Auth auth = auth(access, refresh); return rotate(auth, polls.closeAs(id, auth.me().body().userUuid()));
    }
    public AuthorizedResult<PollDetailResponse> image(Long id, Long optionId, MultipartFile file,
                                                       String access, String refresh) {
        Auth auth = auth(access, refresh); return rotate(auth, polls.replaceOptionImageAs(id, optionId, file));
    }
    public AuthorizedResult<PollDetailResponse> coverImage(Long id, MultipartFile file, String access, String refresh) {
        Auth auth = auth(access, refresh);
        return rotate(auth, polls.replaceCoverImageAs(id, auth.me().body().userUuid(), file));
    }
    public AuthorizedResult<PollDetailResponse> deleteCoverImage(Long id, String access, String refresh) {
        Auth auth = auth(access, refresh);
        return rotate(auth, polls.removeCoverImageAs(id, auth.me().body().userUuid()));
    }
    public AuthorizedResult<PollDetailResponse> deleteImage(Long id, Long optionId, String access, String refresh) {
        Auth auth = auth(access, refresh); return rotate(auth, polls.removeOptionImageAs(id, optionId));
    }
    public AuthorizedResult<PollDetailResponse> uploadQuestionMedia(Long id, Long questionId,
            List<MultipartFile> files, String access, String refresh) {
        Auth auth = auth(access, refresh);
        return rotate(auth, polls.uploadQuestionMediaAs(id, questionId, files));
    }
    public AuthorizedResult<PollDetailResponse> reorderQuestionMedia(Long id, Long questionId,
            PollMediaOrderRequest body, String access, String refresh) {
        Auth auth = auth(access, refresh);
        return rotate(auth, polls.reorderQuestionMediaAs(id, questionId, body.mediaIds()));
    }
    public AuthorizedResult<PollDetailResponse> removeQuestionMedia(Long id, Long questionId, Long mediaId,
            String access, String refresh) {
        Auth auth = auth(access, refresh);
        return rotate(auth, polls.removeQuestionMediaAs(id, questionId, mediaId));
    }
    public AuthorizedResult<Void> delete(Long id, boolean force, String access, String refresh) {
        Auth auth = auth(access, refresh); polls.deleteAs(id, auth.role(), force); return rotate(auth, null);
    }

    private Auth auth(String access, String refresh) {
        AuthorizedResult<MeResponse> me = users.getMe(access, refresh);
        FestivalRole role = me.body().festivalRoles().contains(FestivalRole.SUPER_ADMIN)
                ? FestivalRole.SUPER_ADMIN : me.body().festivalRoles().contains(FestivalRole.ADMIN)
                ? FestivalRole.ADMIN : null;
        if (role == null) throw new ApiException(HttpStatus.FORBIDDEN, "POLL_MANAGE_FORBIDDEN", "투표 관리 권한이 없습니다.");
        return new Auth(me, role);
    }
    private <T> AuthorizedResult<T> rotate(Auth auth, T body) {
        return new AuthorizedResult<>(body, auth.me().newAccessToken(), auth.me().newRefreshToken());
    }
    private record Auth(AuthorizedResult<MeResponse> me, FestivalRole role) { }
}
