package org.syu_likelion.Festa_2026.poll;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.syu_likelion.Festa_2026.auth.AuthorizedSsoExecutor.AuthorizedResult;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.poll.PollDtos.AdminSubmissionResponse;
import org.syu_likelion.Festa_2026.poll.PollDtos.AdminAnswerResponse;
import org.syu_likelion.Festa_2026.poll.PollDtos.MyAnswerResponse;
import org.syu_likelion.Festa_2026.poll.PollDtos.MySubmissionResponse;
import org.syu_likelion.Festa_2026.poll.PollDtos.OptionMutationRequest;
import org.syu_likelion.Festa_2026.poll.PollDtos.OptionResultResponse;
import org.syu_likelion.Festa_2026.poll.PollDtos.PollAdminDetailResponse;
import org.syu_likelion.Festa_2026.poll.PollDtos.PollAnswerRequest;
import org.syu_likelion.Festa_2026.poll.PollDtos.PollDetailResponse;
import org.syu_likelion.Festa_2026.poll.PollDtos.PollMutationRequest;
import org.syu_likelion.Festa_2026.poll.PollDtos.PollOptionResponse;
import org.syu_likelion.Festa_2026.poll.PollDtos.PollQuestionResponse;
import org.syu_likelion.Festa_2026.poll.PollDtos.PollQuestionMediaResponse;
import org.syu_likelion.Festa_2026.poll.PollDtos.PollResultResponse;
import org.syu_likelion.Festa_2026.poll.PollDtos.PollSettingsRequest;
import org.syu_likelion.Festa_2026.poll.PollDtos.PollSubmissionRequest;
import org.syu_likelion.Festa_2026.poll.PollDtos.PollSummaryResponse;
import org.syu_likelion.Festa_2026.poll.PollDtos.QuestionMutationRequest;
import org.syu_likelion.Festa_2026.poll.PollDtos.QuestionResultResponse;
import org.syu_likelion.Festa_2026.poll.PollDtos.SubmissionReceipt;
import org.syu_likelion.Festa_2026.poll.PollDtos.TextResultResponse;
import org.syu_likelion.Festa_2026.poll.PollOptionImageStorage.StoredImage;
import org.syu_likelion.Festa_2026.poll.PollQuestionMediaStorage.StoredMedia;
import org.syu_likelion.Festa_2026.sso.SsoInternalProfileClient;
import org.syu_likelion.Festa_2026.sso.SsoProfiles.InternalUserProfile;
import org.syu_likelion.Festa_2026.storage.TransactionalFileActions;
import org.syu_likelion.Festa_2026.user.FestivalRole;
import org.syu_likelion.Festa_2026.user.UserDtos.MeResponse;
import org.syu_likelion.Festa_2026.user.UserService;

@Service
public class PollService {
    private final FestivalPollRepository polls;
    private final PollSubmissionRepository submissions;
    private final PollOptionRepository options;
    private final PollQuestionRepository questions;
    private final PollQuestionMediaRepository questionMedia;
    private final PollOptionImageStorage images;
    private final PollCoverImageStorage coverImages;
    private final PollQuestionMediaStorage questionMediaStorage;
    private final UserService users;
    private final SsoInternalProfileClient profiles;
    private final Clock clock;

    public PollService(FestivalPollRepository polls, PollSubmissionRepository submissions,
                       PollOptionRepository options, PollQuestionRepository questions,
                       PollQuestionMediaRepository questionMedia, PollOptionImageStorage images, PollCoverImageStorage coverImages,
                       PollQuestionMediaStorage questionMediaStorage, UserService users,
                       SsoInternalProfileClient profiles, Clock clock) {
        this.polls = polls; this.submissions = submissions; this.options = options; this.questions = questions;
        this.questionMedia = questionMedia; this.images = images; this.coverImages = coverImages; this.questionMediaStorage = questionMediaStorage;
        this.users = users; this.profiles = profiles; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AuthorizedResult<List<PollSummaryResponse>> listVisible(String access, String refresh) {
        AuthorizedResult<MeResponse> auth = users.getMe(access, refresh);
        users.requireSchoolVerified(auth.body().userUuid());
        Instant now = clock.instant();
        List<PollSummaryResponse> result = polls.findByPublishedAtLessThanEqualOrderByStartsAtAsc(now).stream()
                .map(poll -> toSummary(poll, auth.body().userUuid(), now))
                .sorted(Comparator.comparingInt((PollSummaryResponse item) -> stateOrder(item.state()))
                        .thenComparing(PollSummaryResponse::startsAt))
                .toList();
        return rotated(auth, result);
    }

    @Transactional(readOnly = true)
    public AuthorizedResult<PollDetailResponse> getVisible(Long id, String access, String refresh) {
        AuthorizedResult<MeResponse> auth = users.getMe(access, refresh);
        users.requireSchoolVerified(auth.body().userUuid());
        FestivalPoll poll = visible(id, clock.instant());
        return rotated(auth, toDetail(poll, auth.body().userUuid(), clock.instant()));
    }

    @Transactional
    public AuthorizedResult<SubmissionReceipt> submit(Long id, PollSubmissionRequest request,
                                                       String access, String refresh) {
        AuthorizedResult<MeResponse> auth = users.getMe(access, refresh);
        users.requireSchoolVerified(auth.body().userUuid());
        SubmissionReceipt receipt = submitAs(id, auth.body().userUuid(), request);
        return rotated(auth, receipt);
    }

    @Transactional(readOnly = true)
    public AuthorizedResult<List<MySubmissionResponse>> mySubmissions(Long id, String access, String refresh) {
        AuthorizedResult<MeResponse> auth = users.getMe(access, refresh);
        users.requireSchoolVerified(auth.body().userUuid());
        visible(id, clock.instant());
        List<MySubmissionResponse> body = submissions
                .findByPollIdAndUserUuidOrderBySubmittedAtDesc(id, auth.body().userUuid())
                .stream().map(this::toMySubmission).toList();
        return rotated(auth, body);
    }

    @Transactional(readOnly = true)
    public AuthorizedResult<PollResultResponse> publicResults(Long id, String access, String refresh) {
        AuthorizedResult<MeResponse> auth = users.getMe(access, refresh);
        users.requireSchoolVerified(auth.body().userUuid());
        FestivalPoll poll = visible(id, clock.instant());
        if (!resultAvailable(poll, clock.instant()))
            throw new ApiException(HttpStatus.FORBIDDEN, "POLL_RESULT_NOT_AVAILABLE", "아직 공개되지 않은 투표 결과입니다.");
        return rotated(auth, results(poll, clock.instant()));
    }

    @Transactional(readOnly = true)
    public List<PollSummaryResponse> listAdmin() {
        Instant now = clock.instant();
        return polls.findAllByOrderByCreatedAtDesc().stream().map(poll -> toSummary(poll, null, now)).toList();
    }

    @Transactional(readOnly = true)
    public PollAdminDetailResponse adminDetailAs(Long id, FestivalRole viewerRole) {
        return adminDetailAs(id, viewerRole, 0, 50);
    }

    @Transactional(readOnly = true)
    public PollAdminDetailResponse adminDetailAs(Long id, FestivalRole viewerRole, int page, int size) {
        FestivalPoll poll = find(id);
        requireAdmin(viewerRole);
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        Page<PollSubmission> submissionPage = submissions.findByPollId(id, PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Order.desc("submittedAt"), Sort.Order.desc("id"))));
        List<PollSubmission> all = submissionPage.getContent();
        boolean revealIdentity = !poll.isAnonymous() || viewerRole == FestivalRole.SUPER_ADMIN;
        Map<UUID, InternalUserProfile> profileMap = revealIdentity ? profileMap(all) : Map.of();
        List<AdminSubmissionResponse> response = all.stream().map(submission -> {
            InternalUserProfile profile = profileMap.get(submission.getUserUuid());
            return new AdminSubmissionResponse(submission.getId(), revealIdentity ? submission.getUserUuid() : null,
                    revealIdentity ? name(profile, submission.getUserUuid()) : null,
                    revealIdentity && profile != null ? profile.studentNo() : null,
                    revealIdentity && profile != null ? profile.department() : null,
                    submission.getSubmittedAt(), adminAnswers(submission));
        }).toList();
        return new PollAdminDetailResponse(toDetail(poll, null, clock.instant()), submissionPage.getTotalElements(),
                results(poll, clock.instant()), response, submissionPage.getNumber(), submissionPage.getSize(),
                submissionPage.getTotalElements(), submissionPage.getTotalPages());
    }

    @Transactional
    public PollDetailResponse createAs(UUID actor, PollMutationRequest request) {
        Normalized definition = normalize(request);
        FestivalPoll poll = new FestivalPoll(definition.title(), definition.description(), definition.anonymous(),
                definition.allowMultiple(), definition.publishedAt(), definition.startsAt(), definition.endsAt(),
                definition.resultPublishedAt(), actor);
        poll.replaceQuestions(buildQuestions(definition.questions(), Map.of(), Map.of()));
        return toDetail(polls.saveAndFlush(poll), null, clock.instant());
    }

    @Transactional
    public PollDetailResponse updateDefinitionAs(Long id, UUID actor, PollMutationRequest request) {
        FestivalPoll poll = find(id);
        if (submissions.existsByPollId(id))
            throw new ApiException(HttpStatus.CONFLICT, "POLL_STRUCTURE_LOCKED",
                    "응답이 시작된 투표의 질문과 선택지는 수정할 수 없습니다.");
        Normalized definition = normalize(request);
        Map<Long, StoredImageData> currentImages = poll.getQuestions().stream()
                .flatMap(question -> question.getOptions().stream())
                .filter(option -> option.getImageUrl() != null)
                .collect(Collectors.toMap(PollOption::getId, option -> new StoredImageData(option.getImageUrl(),
                        option.getImageStorageKey(), option.getImageOriginalFilename())));
        Map<Long, List<StoredQuestionMediaData>> currentQuestionMedia = poll.getQuestions().stream()
                .collect(Collectors.toMap(PollQuestion::getId, question -> question.getMedia().stream()
                        .map(item -> new StoredQuestionMediaData(item.getKind(), item.getUrl(), item.getStorageKey(),
                                item.getOriginalFilename())).toList()));
        Set<Long> retained = definition.questions().stream().flatMap(question -> safe(question.options()).stream())
                .map(OptionMutationRequest::id).filter(currentImages::containsKey).collect(Collectors.toSet());
        List<String> removedKeys = currentImages.entrySet().stream().filter(entry -> !retained.contains(entry.getKey()))
                .map(entry -> entry.getValue().storageKey()).filter(value -> value != null).toList();
        Set<Long> retainedQuestions = definition.questions().stream().map(QuestionMutationRequest::id)
                .filter(currentQuestionMedia::containsKey).collect(Collectors.toSet());
        List<String> removedQuestionMediaKeys = currentQuestionMedia.entrySet().stream()
                .filter(entry -> !retainedQuestions.contains(entry.getKey())).flatMap(entry -> entry.getValue().stream())
                .map(StoredQuestionMediaData::storageKey).toList();
        poll.updateDefinition(definition.title(), definition.description(), definition.anonymous(),
                definition.allowMultiple(), definition.publishedAt(), definition.startsAt(), definition.endsAt(),
                definition.resultPublishedAt(), actor);
        poll.replaceQuestions(buildQuestions(definition.questions(), currentImages, currentQuestionMedia));
        PollDetailResponse response = toDetail(polls.saveAndFlush(poll), null, clock.instant());
        TransactionalFileActions.deleteAfterCommit(() -> removedKeys.forEach(images::delete));
        TransactionalFileActions.deleteAfterCommit(() -> removedQuestionMediaKeys.forEach(questionMediaStorage::delete));
        return response;
    }

    @Transactional
    public PollDetailResponse updateSettingsAs(Long id, UUID actor, PollSettingsRequest request) {
        FestivalPoll poll = find(id);
        String title = required(request.title(), 200, "투표 제목");
        String description = requiredText(request.description(), 5000, "투표 설명");
        if (request.endsAt() == null || !request.endsAt().isAfter(poll.getStartsAt()))
            throw invalid("투표 종료일시는 시작일시보다 늦어야 합니다.");
        poll.updateSettings(title, description, request.endsAt(), request.resultPublishedAt(), actor);
        return toDetail(polls.saveAndFlush(poll), null, clock.instant());
    }

    @Transactional
    public PollDetailResponse closeAs(Long id, UUID actor) {
        FestivalPoll poll = find(id);
        if (state(poll, clock.instant()) == PollState.ENDED)
            throw new ApiException(HttpStatus.CONFLICT, "POLL_ALREADY_ENDED", "이미 종료된 투표입니다.");
        poll.close(clock.instant(), actor);
        return toDetail(polls.saveAndFlush(poll), null, clock.instant());
    }

    @Transactional
    public void deleteAs(Long id, FestivalRole role, boolean force) {
        FestivalPoll poll = find(id);
        boolean hasResponses = submissions.existsByPollId(id);
        if (hasResponses && !(force && role == FestivalRole.SUPER_ADMIN))
            throw new ApiException(HttpStatus.CONFLICT, "POLL_DELETE_LOCKED",
                    "응답이 있는 투표는 SUPER_ADMIN만 강제 삭제할 수 있습니다.");
        List<String> keys = poll.getQuestions().stream().flatMap(question -> question.getOptions().stream())
                .map(PollOption::getImageStorageKey).filter(value -> value != null && !value.isBlank()).toList();
        List<String> questionMediaKeys = poll.getQuestions().stream().flatMap(question -> question.getMedia().stream())
                .map(PollQuestionMedia::getStorageKey).toList();
        if (hasResponses) {
            submissions.deleteByPollId(id);
            submissions.flush();
        }
        String coverKey = poll.getImageStorageKey();
        polls.delete(poll);
        polls.flush();
        TransactionalFileActions.deleteAfterCommit(() -> keys.forEach(images::delete));
        if (coverKey != null) TransactionalFileActions.deleteAfterCommit(() -> coverImages.delete(coverKey));
        TransactionalFileActions.deleteAfterCommit(() -> questionMediaKeys.forEach(questionMediaStorage::delete));
    }

    @Transactional
    public PollDetailResponse replaceCoverImageAs(Long pollId, UUID actor, MultipartFile file) {
        FestivalPoll poll = find(pollId);
        var stored = coverImages.store(file);
        String oldKey = poll.getImageStorageKey();
        boolean rollbackCleanup = TransactionalFileActions.deleteOnRollback(() -> coverImages.delete(stored.storageKey()));
        try {
            poll.setImage(stored.url(), stored.storageKey(), stored.originalFilename(), actor);
            polls.saveAndFlush(poll);
            if (oldKey != null) TransactionalFileActions.deleteAfterCommit(() -> coverImages.delete(oldKey));
            return toDetail(poll, null, clock.instant());
        } catch (RuntimeException failure) {
            if (!rollbackCleanup) coverImages.delete(stored.storageKey());
            throw failure;
        }
    }

    @Transactional
    public PollDetailResponse removeCoverImageAs(Long pollId, UUID actor) {
        FestivalPoll poll = find(pollId);
        String oldKey = poll.getImageStorageKey();
        poll.setImage(null, null, null, actor);
        polls.saveAndFlush(poll);
        if (oldKey != null) TransactionalFileActions.deleteAfterCommit(() -> coverImages.delete(oldKey));
        return toDetail(poll, null, clock.instant());
    }

    @Transactional
    public PollDetailResponse replaceOptionImageAs(Long pollId, Long optionId, MultipartFile file) {
        FestivalPoll poll = find(pollId);
        PollOption option = options.findByIdAndQuestionPollId(optionId, pollId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "POLL_OPTION_NOT_FOUND", "선택지를 찾을 수 없습니다."));
        StoredImage stored = images.store(file);
        String oldKey = option.getImageStorageKey();
        boolean rollbackCleanup = TransactionalFileActions.deleteOnRollback(() -> images.delete(stored.storageKey()));
        try {
            option.setImage(stored.url(), stored.storageKey(), stored.originalFilename());
            options.saveAndFlush(option);
            if (oldKey != null) TransactionalFileActions.deleteAfterCommit(() -> images.delete(oldKey));
            return toDetail(poll, null, clock.instant());
        } catch (RuntimeException failure) {
            if (!rollbackCleanup) images.delete(stored.storageKey());
            throw failure;
        }
    }

    @Transactional
    public PollDetailResponse removeOptionImageAs(Long pollId, Long optionId) {
        FestivalPoll poll = find(pollId);
        PollOption option = options.findByIdAndQuestionPollId(optionId, pollId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "POLL_OPTION_NOT_FOUND", "선택지를 찾을 수 없습니다."));
        String oldKey = option.getImageStorageKey();
        option.setImage(null, null, null);
        options.saveAndFlush(option);
        if (oldKey != null) TransactionalFileActions.deleteAfterCommit(() -> images.delete(oldKey));
        return toDetail(poll, null, clock.instant());
    }

    @Transactional
    public PollDetailResponse uploadQuestionMediaAs(Long pollId, Long questionId, List<MultipartFile> files) {
        requireStructureUnlocked(pollId);
        FestivalPoll poll = find(pollId);
        PollQuestion question = findQuestion(pollId, questionId);
        List<MultipartFile> uploads = files == null ? List.of() : files.stream()
                .filter(file -> file != null && !file.isEmpty()).toList();
        if (uploads.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "POLL_QUESTION_MEDIA_REQUIRED",
                "업로드할 질문 미디어를 선택해 주세요.");
        if (question.getMedia().size() + uploads.size() > 3)
            throw new ApiException(HttpStatus.BAD_REQUEST, "POLL_QUESTION_MEDIA_LIMIT_EXCEEDED",
                    "질문별 이미지와 동영상은 합해 최대 3개입니다.");
        List<StoredMedia> stored = new ArrayList<>();
        boolean rollbackCleanup = TransactionalFileActions.deleteOnRollback(() ->
                stored.forEach(item -> questionMediaStorage.delete(item.storageKey())));
        try {
            for (MultipartFile file : uploads) {
                StoredMedia item = questionMediaStorage.store(file);
                stored.add(item);
                question.addMedia(new PollQuestionMedia(item.kind(), item.url(), item.storageKey(),
                        item.originalFilename(), question.getMedia().size()));
            }
            questions.saveAndFlush(question);
            return toDetail(poll, null, clock.instant());
        } catch (RuntimeException failure) {
            if (!rollbackCleanup) stored.forEach(item -> questionMediaStorage.delete(item.storageKey()));
            throw failure;
        }
    }

    @Transactional
    public PollDetailResponse removeQuestionMediaAs(Long pollId, Long questionId, Long mediaId) {
        requireStructureUnlocked(pollId);
        FestivalPoll poll = find(pollId);
        PollQuestion question = findQuestion(pollId, questionId);
        PollQuestionMedia item = questionMedia.findByIdAndQuestionIdAndQuestionPollId(mediaId, questionId, pollId)
                .orElseThrow(this::questionMediaNotFound);
        String key = item.getStorageKey();
        question.removeMedia(item);
        question.reorderMedia(question.getMedia());
        questions.saveAndFlush(question);
        TransactionalFileActions.deleteAfterCommit(() -> questionMediaStorage.delete(key));
        return toDetail(poll, null, clock.instant());
    }

    @Transactional
    public PollDetailResponse reorderQuestionMediaAs(Long pollId, Long questionId, List<Long> mediaIds) {
        requireStructureUnlocked(pollId);
        FestivalPoll poll = find(pollId);
        PollQuestion question = findQuestion(pollId, questionId);
        List<Long> requested = mediaIds == null ? List.of() : mediaIds;
        Map<Long, PollQuestionMedia> current = question.getMedia().stream()
                .collect(Collectors.toMap(PollQuestionMedia::getId, Function.identity()));
        if (requested.size() != current.size() || requested.size() != new HashSet<>(requested).size()
                || !current.keySet().containsAll(requested))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_POLL_QUESTION_MEDIA_ORDER",
                    "현재 질문 미디어 전체를 중복 없이 순서대로 보내 주세요.");
        question.reorderMedia(requested.stream().map(current::get).toList());
        questions.saveAndFlush(question);
        return toDetail(poll, null, clock.instant());
    }

    @Transactional
    public SubmissionReceipt submitAs(Long id, UUID userUuid, PollSubmissionRequest request) {
        Instant now = clock.instant();
        FestivalPoll poll = visible(id, now);
        if (state(poll, now) != PollState.OPEN)
            throw new ApiException(HttpStatus.CONFLICT, "POLL_NOT_OPEN", "현재 응답할 수 없는 투표입니다.");
        if (!poll.isAllowMultipleSubmissions() && submissions.existsByPollIdAndUserUuid(id, userUuid))
            throw alreadySubmitted();
        Map<Long, PollAnswerRequest> provided = new LinkedHashMap<>();
        for (PollAnswerRequest answer : request.answers()) {
            if (provided.putIfAbsent(answer.questionId(), answer) != null)
                throw invalid("같은 질문에 대한 응답이 중복되었습니다.");
        }
        Set<Long> questionIds = poll.getQuestions().stream().map(PollQuestion::getId).collect(Collectors.toSet());
        if (!questionIds.containsAll(provided.keySet())) throw invalid("이 투표에 속하지 않은 질문이 포함되었습니다.");
        PollSubmission submission = new PollSubmission(poll, userUuid, now);
        for (PollQuestion question : poll.getQuestions()) {
            PollAnswer answer = validateAnswer(question, provided.get(question.getId()));
            if (answer != null) submission.addAnswer(answer);
        }
        try {
            PollSubmission saved = submissions.saveAndFlush(submission);
            return new SubmissionReceipt(saved.getId(), saved.getSubmittedAt(),
                    submissions.countByPollIdAndUserUuid(id, userUuid));
        } catch (DataIntegrityViolationException duplicate) {
            throw alreadySubmitted();
        }
    }

    private PollAnswer validateAnswer(PollQuestion question, PollAnswerRequest request) {
        if (request == null) {
            if (question.isRequired()) throw invalid("필수 질문에 응답해 주세요: " + question.getText());
            return null;
        }
        if (question.getType().isChoice()) {
            List<Long> ids = request.optionIds() == null ? List.of() : request.optionIds();
            if (ids.size() != new HashSet<>(ids).size()) throw invalid("같은 선택지를 중복 선택할 수 없습니다.");
            if (question.isRequired() && ids.isEmpty()) throw invalid("필수 질문에 응답해 주세요: " + question.getText());
            if (question.getType() == PollQuestionType.SINGLE_CHOICE && ids.size() > 1)
                throw invalid("단일 선택 질문에서는 하나만 선택할 수 있습니다.");
            Map<Long, PollOption> allowed = question.getOptions().stream()
                    .collect(Collectors.toMap(PollOption::getId, Function.identity()));
            if (!allowed.keySet().containsAll(ids)) throw invalid("이 질문에 속하지 않은 선택지가 포함되었습니다.");
            if (ids.isEmpty()) return null;
            return new PollAnswer(question, null, ids.stream().map(allowed::get).toList());
        }
        if (request.optionIds() != null && !request.optionIds().isEmpty())
            throw invalid("주관식 질문에는 선택지를 제출할 수 없습니다.");
        String text = request.text() == null ? "" : request.text().trim();
        if (question.isRequired() && text.isEmpty()) throw invalid("필수 질문에 응답해 주세요: " + question.getText());
        int max = question.getType() == PollQuestionType.SHORT_TEXT ? 500 : 5000;
        if (text.length() > max) throw invalid("주관식 응답은 최대 " + max + "자입니다.");
        return text.isEmpty() ? null : new PollAnswer(question, text, List.of());
    }

    private Normalized normalize(PollMutationRequest request) {
        String title = required(request.title(), 200, "투표 제목");
        String description = requiredText(request.description(), 5000, "투표 설명");
        if (request.publishedAt() == null || request.startsAt() == null || request.endsAt() == null)
            throw invalid("공개, 시작, 종료일시는 모두 필요합니다.");
        if (!request.endsAt().isAfter(request.startsAt())) throw invalid("투표 종료일시는 시작일시보다 늦어야 합니다.");
        List<QuestionMutationRequest> questions = safe(request.questions());
        if (questions.isEmpty() || questions.size() > 50) throw invalid("질문은 1개 이상 50개 이하로 등록해 주세요.");
        for (QuestionMutationRequest question : questions) {
            required(question.text(), 500, "질문");
            if (question.type() == null) throw invalid("질문 유형을 선택해 주세요.");
            List<OptionMutationRequest> choices = safe(question.options());
            if (question.type().isChoice()) {
                if (choices.size() < 2 || choices.size() > 30) throw invalid("객관식 질문의 선택지는 2~30개여야 합니다.");
                Set<String> duplicate = new HashSet<>();
                for (OptionMutationRequest option : choices) {
                    String value = required(option.text(), 200, "선택지");
                    if (!duplicate.add(value)) throw invalid("같은 질문에 중복된 선택지가 있습니다.");
                }
            } else if (!choices.isEmpty()) throw invalid("주관식 질문에는 선택지를 등록할 수 없습니다.");
        }
        return new Normalized(title, description, request.anonymous(), request.allowMultipleSubmissions(),
                request.publishedAt(), request.startsAt(), request.endsAt(), request.resultPublishedAt(), questions);
    }

    private List<PollQuestion> buildQuestions(List<QuestionMutationRequest> requests,
                                               Map<Long, StoredImageData> currentImages,
                                               Map<Long, List<StoredQuestionMediaData>> currentQuestionMedia) {
        List<PollQuestion> result = new ArrayList<>();
        for (int questionIndex = 0; questionIndex < requests.size(); questionIndex++) {
            QuestionMutationRequest request = requests.get(questionIndex);
            PollQuestion question = new PollQuestion(request.text().trim(), request.type(), request.required(), questionIndex);
            List<OptionMutationRequest> optionRequests = safe(request.options());
            for (int optionIndex = 0; optionIndex < optionRequests.size(); optionIndex++) {
                OptionMutationRequest optionRequest = optionRequests.get(optionIndex);
                PollOption option = new PollOption(optionRequest.text().trim(), optionIndex);
                StoredImageData image = optionRequest.id() == null ? null : currentImages.get(optionRequest.id());
                if (image != null) option.setImage(image.url(), image.storageKey(), image.originalFilename());
                question.addOption(option);
            }
            List<StoredQuestionMediaData> preservedMedia = request.id() == null ? List.of()
                    : currentQuestionMedia.getOrDefault(request.id(), List.of());
            for (StoredQuestionMediaData item : preservedMedia)
                question.addMedia(new PollQuestionMedia(item.kind(), item.url(), item.storageKey(),
                        item.originalFilename(), question.getMedia().size()));
            result.add(question);
        }
        return result;
    }

    private PollResultResponse results(FestivalPoll poll, Instant now) {
        List<PollSubmission> all = submissions.findByPollIdOrderBySubmittedAtDesc(poll.getId());
        List<QuestionResultResponse> questions = poll.getQuestions().stream().map(question -> {
            List<PollAnswer> answers = all.stream().flatMap(item -> item.getAnswers().stream())
                    .filter(answer -> answer.getQuestion().getId().equals(question.getId())).toList();
            List<OptionResultResponse> optionResults = question.getOptions().stream().map(option -> {
                long count = answers.stream().filter(answer -> answer.getSelectedOptions().stream()
                        .anyMatch(selected -> selected.getId().equals(option.getId()))).count();
                double percentage = answers.isEmpty() ? 0d : Math.round(count * 1000d / answers.size()) / 10d;
                return new OptionResultResponse(option.getId(), option.getText(), option.getImageUrl(), count, percentage);
            }).toList();
            List<TextResultResponse> texts = question.getType().isChoice() ? List.of() : all.stream()
                    .flatMap(submission -> submission.getAnswers().stream()
                            .filter(answer -> answer.getQuestion().getId().equals(question.getId()))
                            .map(answer -> new TextResultResponse(answer.getTextValue(), submission.getSubmittedAt())))
                    .toList();
            return new QuestionResultResponse(question.getId(), question.getText(), question.getType(),
                    answers.size(), optionResults, texts);
        }).toList();
        return new PollResultResponse(poll.getId(), poll.getTitle(), all.size(), now, questions);
    }

    private PollSummaryResponse toSummary(FestivalPoll poll, UUID viewer, Instant now) {
        long mine = viewer == null ? 0 : submissions.countByPollIdAndUserUuid(poll.getId(), viewer);
        return new PollSummaryResponse(poll.getId(), poll.getTitle(), poll.getDescription(), poll.isAnonymous(),
                poll.isAllowMultipleSubmissions(), poll.getPublishedAt(), poll.getStartsAt(), poll.getEndsAt(),
                poll.getResultPublishedAt(), poll.getClosedAt(), state(poll, now), mine > 0, mine,
                resultAvailable(poll, now), poll.getImageUrl());
    }

    private PollDetailResponse toDetail(FestivalPoll poll, UUID viewer, Instant now) {
        PollSummaryResponse summary = toSummary(poll, viewer, now);
        return new PollDetailResponse(summary.id(), summary.title(), summary.description(), summary.anonymous(),
                summary.allowMultipleSubmissions(), summary.publishedAt(), summary.startsAt(), summary.endsAt(),
                summary.resultPublishedAt(), summary.closedAt(), summary.state(), summary.hasSubmitted(),
                summary.mySubmissionCount(), summary.resultAvailable(), poll.getQuestions().stream().map(question ->
                new PollQuestionResponse(question.getId(), question.getText(), question.getType(), question.isRequired(),
                        question.getOptions().stream().map(option -> new PollOptionResponse(option.getId(),
                                option.getText(), option.getImageUrl())).toList(),
                        question.getMedia().stream().map(item -> new PollQuestionMediaResponse(item.getId(),
                                item.getKind(), item.getUrl(), item.getOriginalFilename(), item.getDisplayOrder()))
                                .toList())).toList(),
                poll.getCreatedAt(), poll.getUpdatedAt(), poll.getImageUrl());
    }

    private MySubmissionResponse toMySubmission(PollSubmission submission) {
        return new MySubmissionResponse(submission.getId(), submission.getSubmittedAt(), answers(submission));
    }

    private List<MyAnswerResponse> answers(PollSubmission submission) {
        return submission.getAnswers().stream().map(answer -> new MyAnswerResponse(answer.getQuestion().getId(),
                answer.getSelectedOptions().stream().map(PollOption::getId).toList(), answer.getTextValue())).toList();
    }

    private List<AdminAnswerResponse> adminAnswers(PollSubmission submission) {
        return submission.getAnswers().stream().map(answer -> new AdminAnswerResponse(
                answer.getQuestion().getId(), answer.getQuestion().getText(),
                answer.getSelectedOptions().stream().map(PollOption::getId).toList(),
                answer.getSelectedOptions().stream().map(PollOption::getText).toList(), answer.getTextValue())).toList();
    }

    private FestivalPoll visible(Long id, Instant now) {
        return polls.findByIdAndPublishedAtLessThanEqual(id, now).orElseThrow(this::notFound);
    }
    private FestivalPoll find(Long id) { return polls.findById(id).orElseThrow(this::notFound); }
    private PollQuestion findQuestion(Long pollId, Long questionId) {
        return questions.findByIdAndPollId(questionId, pollId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "POLL_QUESTION_NOT_FOUND", "질문을 찾을 수 없습니다."));
    }
    private void requireStructureUnlocked(Long pollId) {
        if (submissions.existsByPollId(pollId)) throw new ApiException(HttpStatus.CONFLICT,
                "POLL_STRUCTURE_LOCKED", "응답이 시작된 투표의 질문 미디어는 변경할 수 없습니다.");
    }
    private PollState state(FestivalPoll poll, Instant now) {
        if (poll.getClosedAt() != null || !now.isBefore(poll.getEndsAt())) return PollState.ENDED;
        return now.isBefore(poll.getStartsAt()) ? PollState.UPCOMING : PollState.OPEN;
    }
    private int stateOrder(PollState state) { return switch (state) { case OPEN -> 0; case UPCOMING -> 1; case ENDED -> 2; }; }
    private boolean resultAvailable(FestivalPoll poll, Instant now) {
        return poll.getResultPublishedAt() != null && !now.isBefore(poll.getResultPublishedAt());
    }
    private void requireAdmin(FestivalRole role) {
        if (role != FestivalRole.ADMIN && role != FestivalRole.SUPER_ADMIN)
            throw new ApiException(HttpStatus.FORBIDDEN, "POLL_MANAGE_FORBIDDEN", "투표 관리 권한이 없습니다.");
    }
    private String required(String value, int max, String label) {
        if (value == null || value.trim().isEmpty()) throw invalid(label + "을(를) 입력해 주세요.");
        if (value.trim().length() > max) throw invalid(label + "은(는) 최대 " + max + "자입니다.");
        return value.trim();
    }
    private String requiredText(String value, int max, String label) {
        if (value == null) throw invalid(label + "을(를) 입력해 주세요.");
        if (value.trim().length() > max) throw invalid(label + "은(는) 최대 " + max + "자입니다.");
        return value.trim();
    }
    private Map<UUID, InternalUserProfile> profileMap(List<PollSubmission> all) {
        Set<UUID> ids = all.stream().map(PollSubmission::getUserUuid)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, InternalUserProfile> result = new HashMap<>();
        List<UUID> list = new ArrayList<>(ids);
        for (int start = 0; start < list.size(); start += 100) {
            try { profiles.getProfiles(list.subList(start, Math.min(start + 100, list.size())))
                    .forEach(profile -> result.put(profile.userUuid(), profile)); }
            catch (RuntimeException ignored) { }
        }
        return result;
    }
    private String name(InternalUserProfile profile, UUID fallback) {
        if (org.syu_likelion.Festa_2026.user.DeletedUserIdentity.matches(fallback))
            return org.syu_likelion.Festa_2026.user.DeletedUserIdentity.NAME;
        return profile == null || profile.name() == null || profile.name().isBlank()
                ? fallback.toString() : profile.name();
    }
    private ApiException invalid(String message) { return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_POLL", message); }
    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "POLL_NOT_FOUND", "투표를 찾을 수 없습니다."); }
    private ApiException alreadySubmitted() { return new ApiException(HttpStatus.CONFLICT, "POLL_ALREADY_SUBMITTED", "이미 참여한 투표입니다."); }
    private ApiException questionMediaNotFound() { return new ApiException(HttpStatus.NOT_FOUND,
            "POLL_QUESTION_MEDIA_NOT_FOUND", "질문 미디어를 찾을 수 없습니다."); }
    private <T> List<T> safe(List<T> values) { return values == null ? List.of() : values; }
    private <T> AuthorizedResult<T> rotated(AuthorizedResult<MeResponse> auth, T body) {
        return new AuthorizedResult<>(body, auth.newAccessToken(), auth.newRefreshToken());
    }
    private record Normalized(String title, String description, boolean anonymous, boolean allowMultiple,
                              Instant publishedAt, Instant startsAt, Instant endsAt, Instant resultPublishedAt,
                              List<QuestionMutationRequest> questions) { }
    private record StoredImageData(String url, String storageKey, String originalFilename) { }
    private record StoredQuestionMediaData(PollQuestionMediaKind kind, String url, String storageKey,
                                           String originalFilename) { }
}
