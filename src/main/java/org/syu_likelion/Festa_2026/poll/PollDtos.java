package org.syu_likelion.Festa_2026.poll;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PollDtos {
    private PollDtos() { }

    public record PollMutationRequest(
            @NotBlank @Size(max = 200) String title,
            @NotNull @Size(max = 5000) String description,
            boolean anonymous,
            boolean allowMultipleSubmissions,
            @NotNull Instant publishedAt,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt,
            Instant resultPublishedAt,
            @NotEmpty @Size(max = 50) List<@Valid QuestionMutationRequest> questions) { }

    public record QuestionMutationRequest(
            Long id,
            @NotBlank @Size(max = 500) String text,
            @NotNull PollQuestionType type,
            boolean required,
            @Size(max = 30) List<@Valid OptionMutationRequest> options) { }

    public record OptionMutationRequest(Long id, @NotBlank @Size(max = 200) String text) { }

    public record PollSettingsRequest(
            @NotBlank @Size(max = 200) String title,
            @NotNull @Size(max = 5000) String description,
            @NotNull Instant endsAt,
            Instant resultPublishedAt) { }

    public record PollAnswerRequest(
            @NotNull Long questionId,
            List<Long> optionIds,
            @Size(max = 5000) String text) { }

    public record PollSubmissionRequest(
            @NotNull @Size(max = 50) List<@Valid PollAnswerRequest> answers) { }

    public record PollOptionResponse(Long id, String text, String imageUrl) { }
    public record PollQuestionMediaResponse(Long id, PollQuestionMediaKind kind, String url,
                                            String originalFilename, int displayOrder) { }
    public record PollQuestionResponse(Long id, String text, PollQuestionType type, boolean required,
                                       List<PollOptionResponse> options, List<PollQuestionMediaResponse> media) { }

    public record PollMediaOrderRequest(@NotEmpty @Size(max = 3) List<@NotNull Long> mediaIds) { }

    public record PollSummaryResponse(
            Long id, String title, String description, boolean anonymous, boolean allowMultipleSubmissions,
            Instant publishedAt, Instant startsAt, Instant endsAt, Instant resultPublishedAt,
            Instant closedAt, PollState state, boolean hasSubmitted, long mySubmissionCount,
            boolean resultAvailable, String imageUrl) {
        public PollSummaryResponse(
            Long id, String title, String description, boolean anonymous, boolean allowMultipleSubmissions,
            Instant publishedAt, Instant startsAt, Instant endsAt, Instant resultPublishedAt,
            Instant closedAt, PollState state, boolean hasSubmitted, long mySubmissionCount,
            boolean resultAvailable) {
            this(id, title, description, anonymous, allowMultipleSubmissions, publishedAt, startsAt, endsAt, resultPublishedAt, closedAt, state, hasSubmitted, mySubmissionCount, resultAvailable, null);
        }
    }

    public record PollDetailResponse(
            Long id, String title, String description, boolean anonymous, boolean allowMultipleSubmissions,
            Instant publishedAt, Instant startsAt, Instant endsAt, Instant resultPublishedAt,
            Instant closedAt, PollState state, boolean hasSubmitted, long mySubmissionCount,
            boolean resultAvailable, List<PollQuestionResponse> questions,
            Instant createdAt, Instant updatedAt, String imageUrl) {
        public PollDetailResponse(
            Long id, String title, String description, boolean anonymous, boolean allowMultipleSubmissions,
            Instant publishedAt, Instant startsAt, Instant endsAt, Instant resultPublishedAt,
            Instant closedAt, PollState state, boolean hasSubmitted, long mySubmissionCount,
            boolean resultAvailable, List<PollQuestionResponse> questions,
            Instant createdAt, Instant updatedAt) {
            this(id, title, description, anonymous, allowMultipleSubmissions, publishedAt, startsAt, endsAt, resultPublishedAt, closedAt, state, hasSubmitted, mySubmissionCount, resultAvailable, questions, createdAt, updatedAt, null);
        }
    }

    public record SubmissionReceipt(Long submissionId, Instant submittedAt, long mySubmissionCount) { }
    public record MyAnswerResponse(Long questionId, List<Long> optionIds, String text) { }
    public record MySubmissionResponse(Long id, Instant submittedAt, List<MyAnswerResponse> answers) { }

    public record OptionResultResponse(Long optionId, String text, String imageUrl, long count, double percentage) { }
    public record TextResultResponse(String text, Instant submittedAt) { }
    public record QuestionResultResponse(Long questionId, String text, PollQuestionType type,
                                         long answeredCount, List<OptionResultResponse> options,
                                         List<TextResultResponse> textAnswers) { }
    public record PollResultResponse(Long pollId, String title, long submissionCount,
                                     Instant generatedAt, List<QuestionResultResponse> questions) { }

    public record AdminAnswerResponse(Long questionId, String questionText, List<Long> optionIds,
                                      List<String> optionTexts, String text) { }
    public record AdminSubmissionResponse(Long id, UUID userUuid, String userName, String studentNo,
                                          String department, Instant submittedAt,
                                          List<AdminAnswerResponse> answers) { }
    public record PollAdminDetailResponse(PollDetailResponse poll, long submissionCount,
                                          PollResultResponse results,
                                          List<AdminSubmissionResponse> submissions,
                                          int page, int size, long totalElements, int totalPages) { }
}
