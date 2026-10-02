package org.syu_likelion.Festa_2026.poll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.poll.PollDtos.OptionMutationRequest;
import org.syu_likelion.Festa_2026.poll.PollDtos.PollAnswerRequest;
import org.syu_likelion.Festa_2026.poll.PollDtos.PollMutationRequest;
import org.syu_likelion.Festa_2026.poll.PollDtos.PollSubmissionRequest;
import org.syu_likelion.Festa_2026.poll.PollDtos.QuestionMutationRequest;
import org.syu_likelion.Festa_2026.user.FestivalRole;

@SpringBootTest
@Transactional
class PollServiceTests {
    private static final UUID ADMIN = UUID.fromString("123e4567-e89b-12d3-a456-426614174201");
    private static final UUID USER = UUID.fromString("123e4567-e89b-12d3-a456-426614174202");
    private static final UUID USER_TWO = UUID.fromString("123e4567-e89b-12d3-a456-426614174203");

    @Autowired PollService service;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @MockitoBean PollQuestionMediaStorage questionMediaStorage;
    @MockitoBean PollCoverImageStorage coverImageStorage;

    @Test
    void coverImagePersistsAndCanBeReplacedOrRemovedAfterVoting() {
        when(coverImageStorage.store(any())).thenAnswer(invocation -> {
            var upload = (org.springframework.web.multipart.MultipartFile) invocation.getArgument(0);
            return new PollCoverImageStorage.StoredImage("https://cdn.test/" + upload.getOriginalFilename(),
                    "poll/covers/" + upload.getOriginalFilename(), upload.getOriginalFilename());
        });
        var poll = service.createAs(ADMIN, request(false, true));
        service.replaceCoverImageAs(poll.id(), ADMIN, file("cover.png", "image/png"));
        entityManager.flush();
        entityManager.clear();
        assertThat(service.adminDetailAs(poll.id(), FestivalRole.ADMIN).poll().imageUrl())
                .isEqualTo("https://cdn.test/cover.png");
        service.submitAs(poll.id(), USER, choiceAnswer(poll));
        assertThat(service.replaceCoverImageAs(poll.id(), ADMIN, file("new.webp", "image/webp")).imageUrl())
                .isEqualTo("https://cdn.test/new.webp");
        assertThat(service.removeCoverImageAs(poll.id(), ADMIN).imageUrl()).isNull();
        entityManager.flush();
        entityManager.clear();
        assertThat(service.adminDetailAs(poll.id(), FestivalRole.ADMIN).poll().imageUrl()).isNull();
    }

    @Test
    void maximumLengthKoreanAndEmojiPollTextSurvivesDatabaseReload() {
        Instant now = Instant.now();
        String description = "가".repeat(5000);
        String question = "나".repeat(500);
        String option = "다".repeat(200);
        String answer = "🎉".repeat(2500);
        var poll = service.createAs(ADMIN, new PollMutationRequest("라".repeat(200), description,
                true, false, now.minusSeconds(3600), now.minusSeconds(60), now.plusSeconds(3600),
                now.minusSeconds(30), List.of(
                        new QuestionMutationRequest(null, question, PollQuestionType.SINGLE_CHOICE, true,
                                List.of(new OptionMutationRequest(null, option), new OptionMutationRequest(null, "다른 선택지"))),
                        new QuestionMutationRequest(null, question, PollQuestionType.LONG_TEXT, true, List.of()))));
        service.submitAs(poll.id(), USER, new PollSubmissionRequest(List.of(
                new PollAnswerRequest(poll.questions().get(0).id(), List.of(poll.questions().get(0).options().getFirst().id()), null),
                new PollAnswerRequest(poll.questions().get(1).id(), List.of(), answer))));
        entityManager.flush();
        entityManager.clear();
        var reloaded = service.adminDetailAs(poll.id(), FestivalRole.ADMIN);
        assertThat(reloaded.poll().description()).isEqualTo(description);
        assertThat(reloaded.poll().questions().getFirst().text()).isEqualTo(question);
        assertThat(reloaded.poll().questions().getFirst().options().getFirst().text()).isEqualTo(option);
        assertThat(reloaded.results().questions().get(1).textAnswers().getFirst().text()).isEqualTo(answer);
    }

    @Test
    void oversizedPollDescriptionQuestionOptionAndAnswerAreRejectedBeforeSave() {
        Instant now = Instant.now();
        var valid = request(false, true);
        assertThatThrownBy(() -> service.createAs(ADMIN, new PollMutationRequest(valid.title(), "가".repeat(5001),
                true, false, valid.publishedAt(), valid.startsAt(), valid.endsAt(), valid.resultPublishedAt(), valid.questions())))
                .isInstanceOf(ApiException.class);
        for (var question : List.of(
                new QuestionMutationRequest(null, "가".repeat(501), PollQuestionType.LONG_TEXT, true, List.of()),
                new QuestionMutationRequest(null, "질문", PollQuestionType.SINGLE_CHOICE, true,
                        List.of(new OptionMutationRequest(null, "가".repeat(201)))))) {
            assertThatThrownBy(() -> service.createAs(ADMIN, new PollMutationRequest("제목", "설명", true, false,
                    now.minusSeconds(3600), now.minusSeconds(60), now.plusSeconds(3600), null, List.of(question))))
                    .isInstanceOf(ApiException.class);
        }
        var poll = service.createAs(ADMIN, valid);
        assertThatThrownBy(() -> service.submitAs(poll.id(), USER, new PollSubmissionRequest(List.of(
                new PollAnswerRequest(poll.questions().get(0).id(), List.of(poll.questions().get(0).options().getFirst().id()), null),
                new PollAnswerRequest(poll.questions().get(1).id(), List.of(), "가".repeat(5001))))))
                .isInstanceOf(ApiException.class);
        assertThat(service.adminDetailAs(poll.id(), FestivalRole.ADMIN).submissionCount()).isZero();
    }

    @Test
    void questionMediaSupportsMixedUploadIntegratedOrderAndRemoval() {
        when(questionMediaStorage.store(any())).thenAnswer(invocation -> {
            var file = (org.springframework.web.multipart.MultipartFile) invocation.getArgument(0);
            PollQuestionMediaKind kind = file.getContentType().startsWith("image/")
                    ? PollQuestionMediaKind.IMAGE : PollQuestionMediaKind.VIDEO;
            return new PollQuestionMediaStorage.StoredMedia(kind, "https://cdn.test/" + file.getOriginalFilename(),
                    "poll/questions/" + file.getOriginalFilename(), file.getOriginalFilename());
        });
        var poll = service.createAs(ADMIN, request(false, false));
        Long questionId = poll.questions().getFirst().id();
        var uploaded = service.uploadQuestionMediaAs(poll.id(), questionId, List.of(
                file("one.jpg", "image/jpeg"), file("two.mp4", "video/mp4"), file("three.webp", "image/webp")));

        assertThat(uploaded.questions().getFirst().media()).extracting(PollDtos.PollQuestionMediaResponse::kind)
                .containsExactly(PollQuestionMediaKind.IMAGE, PollQuestionMediaKind.VIDEO, PollQuestionMediaKind.IMAGE);
        List<Long> reversed = uploaded.questions().getFirst().media().reversed().stream()
                .map(PollDtos.PollQuestionMediaResponse::id).toList();
        var reordered = service.reorderQuestionMediaAs(poll.id(), questionId, reversed);
        assertThat(reordered.questions().getFirst().media()).extracting(PollDtos.PollQuestionMediaResponse::id)
                .containsExactlyElementsOf(reversed);

        var removed = service.removeQuestionMediaAs(poll.id(), questionId, reversed.get(1));
        assertThat(removed.questions().getFirst().media()).hasSize(2);
        assertThat(removed.questions().getFirst().media()).extracting(PollDtos.PollQuestionMediaResponse::displayOrder)
                .containsExactly(0, 1);
    }

    @Test
    void questionMediaRejectsFourthFileAndLocksAfterFirstSubmission() {
        var poll = service.createAs(ADMIN, request(false, false));
        Long questionId = poll.questions().getFirst().id();
        assertThatThrownBy(() -> service.uploadQuestionMediaAs(poll.id(), questionId, List.of(
                file("1.jpg", "image/jpeg"), file("2.jpg", "image/jpeg"),
                file("3.jpg", "image/jpeg"), file("4.jpg", "image/jpeg"))))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.code()).isEqualTo("POLL_QUESTION_MEDIA_LIMIT_EXCEEDED"));

        service.submitAs(poll.id(), USER, choiceAnswer(poll));
        assertThatThrownBy(() -> service.uploadQuestionMediaAs(poll.id(), questionId,
                List.of(file("locked.jpg", "image/jpeg"))))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.code()).isEqualTo("POLL_STRUCTURE_LOCKED"));
    }

    @Test
    void requiredChoiceAndSubjectiveAnswersAreStoredAndAggregated() {
        var poll = service.createAs(ADMIN, request(false, true));
        Long choiceQuestion = poll.questions().get(0).id();
        Long selectedOption = poll.questions().get(0).options().get(0).id();
        Long textQuestion = poll.questions().get(1).id();

        service.submitAs(poll.id(), USER, new PollSubmissionRequest(List.of(
                new PollAnswerRequest(choiceQuestion, List.of(selectedOption), null),
                new PollAnswerRequest(textQuestion, List.of(), "축제가 기대됩니다."))));

        var admin = service.adminDetailAs(poll.id(), FestivalRole.ADMIN);
        assertThat(admin.submissionCount()).isEqualTo(1);
        assertThat(admin.results().questions().get(0).options().get(0).count()).isEqualTo(1);
        assertThat(admin.results().questions().get(1).textAnswers().getFirst().text()).isEqualTo("축제가 기대됩니다.");
    }

    @Test
    void singleParticipationPollRejectsSecondSubmission() {
        var poll = service.createAs(ADMIN, request(false, false));
        PollSubmissionRequest answer = choiceAnswer(poll);
        service.submitAs(poll.id(), USER, answer);

        assertThatThrownBy(() -> service.submitAs(poll.id(), USER, answer))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.status().value()).isEqualTo(409);
                    assertThat(exception.code()).isEqualTo("POLL_ALREADY_SUBMITTED");
                });
    }

    @Test
    void multipleParticipationPollAcceptsUnlimitedSeparateSubmissions() {
        var poll = service.createAs(ADMIN, request(true, false));
        service.submitAs(poll.id(), USER, choiceAnswer(poll));
        service.submitAs(poll.id(), USER, choiceAnswer(poll));

        assertThat(service.adminDetailAs(poll.id(), FestivalRole.ADMIN).submissionCount()).isEqualTo(2);
    }

    @Test
    void requiredQuestionCannotBeOmitted() {
        var poll = service.createAs(ADMIN, request(false, false));
        assertThatThrownBy(() -> service.submitAs(poll.id(), USER, new PollSubmissionRequest(List.of())))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.code()).isEqualTo("INVALID_POLL"));
    }

    @Test
    void anonymousPollMasksIdentityFromAdmin() {
        var poll = service.createAs(ADMIN, request(false, true));
        service.submitAs(poll.id(), USER, choiceAnswer(poll));

        var submission = service.adminDetailAs(poll.id(), FestivalRole.ADMIN).submissions().getFirst();
        assertThat(submission.userUuid()).isNull();
        assertThat(submission.userName()).isNull();
        assertThat(submission.studentNo()).isNull();
    }

    @Test
    void manualCloseImmediatelyRejectsFurtherSubmissions() {
        var poll = service.createAs(ADMIN, request(true, false));
        service.closeAs(poll.id(), ADMIN);

        assertThatThrownBy(() -> service.submitAs(poll.id(), USER_TWO, choiceAnswer(poll)))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.code()).isEqualTo("POLL_NOT_OPEN"));
    }

    @Test
    void structureCannotChangeAfterFirstResponse() {
        var poll = service.createAs(ADMIN, request(false, false));
        service.submitAs(poll.id(), USER, choiceAnswer(poll));

        assertThatThrownBy(() -> service.updateDefinitionAs(poll.id(), ADMIN, request(false, false)))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.code()).isEqualTo("POLL_STRUCTURE_LOCKED"));
    }

    @Test
    void onlySuperAdminCanForceDeletePollWithResponses() {
        var poll = service.createAs(ADMIN, request(false, false));
        service.submitAs(poll.id(), USER, choiceAnswer(poll));

        assertThatThrownBy(() -> service.deleteAs(poll.id(), FestivalRole.ADMIN, true))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.code()).isEqualTo("POLL_DELETE_LOCKED"));

        service.deleteAs(poll.id(), FestivalRole.SUPER_ADMIN, true);
        assertThatThrownBy(() -> service.adminDetailAs(poll.id(), FestivalRole.SUPER_ADMIN))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.code()).isEqualTo("POLL_NOT_FOUND"));
    }

    private PollSubmissionRequest choiceAnswer(PollDtos.PollDetailResponse poll) {
        return new PollSubmissionRequest(List.of(new PollAnswerRequest(poll.questions().get(0).id(),
                List.of(poll.questions().get(0).options().get(0).id()), null)));
    }

    private MockMultipartFile file(String name, String contentType) {
        return new MockMultipartFile("files", name, contentType, new byte[] { 1, 2, 3 });
    }

    private PollMutationRequest request(boolean multiple, boolean anonymous) {
        Instant now = Instant.now();
        List<QuestionMutationRequest> questions = List.of(
                new QuestionMutationRequest(null, "가장 기대되는 프로그램은?", PollQuestionType.SINGLE_CHOICE,
                        true, List.of(new OptionMutationRequest(null, "공연"), new OptionMutationRequest(null, "부스"))),
                new QuestionMutationRequest(null, "축제에 바라는 점", PollQuestionType.LONG_TEXT,
                        false, List.of()));
        return new PollMutationRequest("축제 사전 설문", "축제 프로그램 선호도 조사", anonymous, multiple,
                now.minusSeconds(3600), now.minusSeconds(60), now.plusSeconds(3600), now.minusSeconds(30), questions);
    }
}
