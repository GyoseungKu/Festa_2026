package org.syu_likelion.Festa_2026.admin;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;
import org.syu_likelion.Festa_2026.poll.PollDtos.OptionMutationRequest;
import org.syu_likelion.Festa_2026.poll.PollDtos.PollDetailResponse;
import org.syu_likelion.Festa_2026.poll.PollDtos.PollMutationRequest;
import org.syu_likelion.Festa_2026.poll.PollDtos.PollSettingsRequest;
import org.syu_likelion.Festa_2026.poll.PollDtos.QuestionMutationRequest;
import org.syu_likelion.Festa_2026.poll.PollQuestionType;
import org.syu_likelion.Festa_2026.poll.PollQuestionMediaKind;

public class PollAdminForm {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private String title;
    private String description = "";
    private boolean anonymous;
    private boolean allowMultipleSubmissions;
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") private LocalDateTime publishedAt;
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") private LocalDateTime startsAt;
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") private LocalDateTime endsAt;
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") private LocalDateTime resultPublishedAt;
    private boolean settingsOnly;
    private List<QuestionForm> questions = new ArrayList<>();

    public static PollAdminForm empty() {
        PollAdminForm form = new PollAdminForm();
        LocalDateTime now = LocalDateTime.now(SEOUL).withSecond(0).withNano(0);
        form.publishedAt = now;
        form.startsAt = now.plusHours(1);
        form.endsAt = now.plusHours(3);
        QuestionForm question = new QuestionForm();
        question.type = PollQuestionType.SINGLE_CHOICE;
        question.required = true;
        question.options.add(new OptionForm());
        question.options.add(new OptionForm());
        form.questions.add(question);
        return form;
    }

    public static PollAdminForm from(PollDetailResponse poll, boolean settingsOnly) {
        PollAdminForm form = new PollAdminForm();
        form.title = poll.title(); form.description = poll.description(); form.anonymous = poll.anonymous();
        form.allowMultipleSubmissions = poll.allowMultipleSubmissions();
        form.publishedAt = local(poll.publishedAt()); form.startsAt = local(poll.startsAt());
        form.endsAt = local(poll.endsAt()); form.resultPublishedAt = local(poll.resultPublishedAt());
        form.settingsOnly = settingsOnly;
        form.questions = poll.questions().stream().map(question -> {
            QuestionForm item = new QuestionForm(); item.id = question.id(); item.text = question.text();
            item.type = question.type(); item.required = question.required();
            item.media = question.media().stream().map(media -> {
                QuestionMediaForm stored = new QuestionMediaForm(); stored.id = media.id(); stored.kind = media.kind();
                stored.url = media.url(); stored.originalFilename = media.originalFilename();
                stored.displayOrder = media.displayOrder(); return stored;
            }).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            item.options = question.options().stream().map(option -> {
                OptionForm choice = new OptionForm(); choice.id = option.id(); choice.text = option.text();
                choice.existingImageUrl = option.imageUrl(); return choice;
            }).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            return item;
        }).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        return form;
    }

    public PollMutationRequest toMutation() {
        return new PollMutationRequest(title, description, anonymous, allowMultipleSubmissions,
                instant(publishedAt), instant(startsAt), instant(endsAt), instant(resultPublishedAt),
                questions == null ? List.of() : questions.stream().map(QuestionForm::toRequest).toList());
    }
    public PollSettingsRequest toSettings() {
        return new PollSettingsRequest(title, description, instant(endsAt), instant(resultPublishedAt));
    }
    private static Instant instant(LocalDateTime value) { return value == null ? null : value.atZone(SEOUL).toInstant(); }
    private static LocalDateTime local(Instant value) { return value == null ? null : LocalDateTime.ofInstant(value, SEOUL); }

    public String getTitle() { return title; } public void setTitle(String value) { title = value; }
    public String getDescription() { return description; } public void setDescription(String value) { description = value; }
    public boolean isAnonymous() { return anonymous; } public void setAnonymous(boolean value) { anonymous = value; }
    public boolean isAllowMultipleSubmissions() { return allowMultipleSubmissions; }
    public void setAllowMultipleSubmissions(boolean value) { allowMultipleSubmissions = value; }
    public LocalDateTime getPublishedAt() { return publishedAt; } public void setPublishedAt(LocalDateTime value) { publishedAt = value; }
    public LocalDateTime getStartsAt() { return startsAt; } public void setStartsAt(LocalDateTime value) { startsAt = value; }
    public LocalDateTime getEndsAt() { return endsAt; } public void setEndsAt(LocalDateTime value) { endsAt = value; }
    public LocalDateTime getResultPublishedAt() { return resultPublishedAt; }
    public void setResultPublishedAt(LocalDateTime value) { resultPublishedAt = value; }
    public boolean isSettingsOnly() { return settingsOnly; } public void setSettingsOnly(boolean value) { settingsOnly = value; }
    public List<QuestionForm> getQuestions() { return questions; } public void setQuestions(List<QuestionForm> value) { questions = value; }

    public static class QuestionForm {
        private Long id; private String text; private PollQuestionType type = PollQuestionType.SINGLE_CHOICE;
        private boolean required; private List<OptionForm> options = new ArrayList<>();
        private List<QuestionMediaForm> media = new ArrayList<>();
        private List<MultipartFile> mediaFiles = new ArrayList<>();
        QuestionMutationRequest toRequest() { return new QuestionMutationRequest(id, text, type, required,
                options == null ? List.of() : options.stream().map(OptionForm::toRequest).toList()); }
        public Long getId() { return id; } public void setId(Long value) { id = value; }
        public String getText() { return text; } public void setText(String value) { text = value; }
        public PollQuestionType getType() { return type; } public void setType(PollQuestionType value) { type = value; }
        public boolean isRequired() { return required; } public void setRequired(boolean value) { required = value; }
        public List<OptionForm> getOptions() { return options; } public void setOptions(List<OptionForm> value) { options = value; }
        public List<QuestionMediaForm> getMedia() { return media; }
        public void setMedia(List<QuestionMediaForm> value) { media = value; }
        public List<MultipartFile> getMediaFiles() { return mediaFiles; }
        public void setMediaFiles(List<MultipartFile> value) { mediaFiles = value; }
    }

    public static class QuestionMediaForm {
        private Long id; private PollQuestionMediaKind kind; private String url; private String originalFilename;
        private int displayOrder; private boolean remove;
        public Long getId() { return id; } public void setId(Long value) { id = value; }
        public PollQuestionMediaKind getKind() { return kind; } public void setKind(PollQuestionMediaKind value) { kind = value; }
        public String getUrl() { return url; } public void setUrl(String value) { url = value; }
        public String getOriginalFilename() { return originalFilename; }
        public void setOriginalFilename(String value) { originalFilename = value; }
        public int getDisplayOrder() { return displayOrder; } public void setDisplayOrder(int value) { displayOrder = value; }
        public boolean isRemove() { return remove; } public void setRemove(boolean value) { remove = value; }
    }

    public static class OptionForm {
        private Long id; private String text; private String existingImageUrl; private boolean removeImage;
        private MultipartFile image;
        OptionMutationRequest toRequest() { return new OptionMutationRequest(id, text); }
        public Long getId() { return id; } public void setId(Long value) { id = value; }
        public String getText() { return text; } public void setText(String value) { text = value; }
        public String getExistingImageUrl() { return existingImageUrl; }
        public void setExistingImageUrl(String value) { existingImageUrl = value; }
        public boolean isRemoveImage() { return removeImage; } public void setRemoveImage(boolean value) { removeImage = value; }
        public MultipartFile getImage() { return image; } public void setImage(MultipartFile value) { image = value; }
    }
}
