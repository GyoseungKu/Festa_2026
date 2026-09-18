package org.syu_likelion.Festa_2026.bamboo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.time.Instant;
import org.syu_likelion.Festa_2026.sso.SsoInternalProfileClient;
import org.syu_likelion.Festa_2026.sso.SsoProfiles.InternalUserProfile;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class BambooReportNotifierTests {
    private static final Long MESSAGE_ID = 42L;
    private static final UUID AUTHOR = UUID.fromString("123e4567-e89b-12d3-a456-426614174100");
    /** 발송을 즉시 수행해 검증이 바로 가능하게 한다. */
    private static final Executor DIRECT = Runnable::run;

    private SsoInternalProfileClient profiles;
    private BambooMessageRepository messages;
    private JavaMailSender sender;
    private ObjectProvider<JavaMailSender> senders;
    private MutableClock clock;
    private org.thymeleaf.spring6.SpringTemplateEngine templates;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        templates = new org.thymeleaf.spring6.SpringTemplateEngine();
        var resolver = new org.thymeleaf.templateresolver.ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        resolver.setTemplateMode("HTML");
        templates.setTemplateResolver(resolver);
        profiles = mock(SsoInternalProfileClient.class);
        when(profiles.getProfile(AUTHOR)).thenReturn(profile("author@example.com"));
        messages = mock(BambooMessageRepository.class);
        sender = mock(JavaMailSender.class);
        senders = mock(ObjectProvider.class);
        clock = new MutableClock(Instant.parse("2026-10-06T10:00:00Z"));
        when(senders.getIfAvailable()).thenReturn(sender);
        when(sender.createMimeMessage()).thenAnswer(ignored -> new JavaMailSenderImpl().createMimeMessage());
        when(messages.findById(MESSAGE_ID)).thenReturn(Optional.of(
                new BambooMessage(7L, AUTHOR, "졸린사자42", "신고가 쌓인 메시지", Instant.now(clock))));
    }

    @Test
    void staysQuietBelowTheThreshold() {
        notifier(properties(5)).notifyIfThresholdReached(MESSAGE_ID, 4);

        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    void sendsOnceTheThresholdIsReached() {
        notifier(properties(5)).notifyIfThresholdReached(MESSAGE_ID, 5);

        verify(sender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void alertsOnlyOncePerMessageEvenAsReportsKeepComing() {
        BambooReportNotifier notifier = notifier(properties(5));

        notifier.notifyIfThresholdReached(MESSAGE_ID, 5);
        notifier.notifyIfThresholdReached(MESSAGE_ID, 6);
        notifier.notifyIfThresholdReached(MESSAGE_ID, 7);

        verify(sender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void doesNothingWhenAuthorHasNoEmail() {
        when(profiles.getProfile(AUTHOR)).thenReturn(profile(null));
        notifier(properties(5)).notifyIfThresholdReached(MESSAGE_ID, 10);

        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    void doesNothingWhenDisabled() {
        BambooAlertProperties disabled = new BambooAlertProperties(false, 5,
                "festa@example.com", "https://example.com", 10, 50);

        notifier(disabled).notifyIfThresholdReached(MESSAGE_ID, 10);

        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    void stopsSendingOnceThePerMinuteBudgetIsSpent() {
        BambooAlertProperties tight = new BambooAlertProperties(true, 1,
                "festa@example.com", "https://example.com", 2, 50);
        BambooReportNotifier notifier = notifier(tight);
        for (long id = 1; id <= 5; id++) stubMessage(id);

        for (long id = 1; id <= 5; id++) notifier.notifyIfThresholdReached(id, 1);

        verify(sender, times(2)).send(any(MimeMessage.class));
    }

    @Test
    void theBudgetRefillsOnTheNextMinute() {
        BambooAlertProperties tight = new BambooAlertProperties(true, 1,
                "festa@example.com", "https://example.com", 1, 50);
        BambooReportNotifier notifier = notifier(tight);
        stubMessage(1L);
        stubMessage(2L);

        notifier.notifyIfThresholdReached(1L, 1);
        notifier.notifyIfThresholdReached(2L, 1);
        verify(sender, times(1)).send(any(MimeMessage.class));

        clock.advance(Duration.ofMinutes(1));
        stubMessage(3L);
        notifier.notifyIfThresholdReached(3L, 1);

        verify(sender, times(2)).send(any(MimeMessage.class));
    }

    @Test
    void aRateLimitedMessageCanBeRetriedAfterTheBudgetRefills() {
        BambooAlertProperties tight = new BambooAlertProperties(true, 1,
                "festa@example.com", "https://example.com", 1, 50);
        BambooReportNotifier notifier = notifier(tight);
        stubMessage(1L);
        stubMessage(2L);

        notifier.notifyIfThresholdReached(1L, 1);
        notifier.notifyIfThresholdReached(2L, 1);
        verify(sender, times(1)).send(any(MimeMessage.class));

        clock.advance(Duration.ofMinutes(1));
        notifier.notifyIfThresholdReached(2L, 2);

        verify(sender, times(2)).send(any(MimeMessage.class));
    }

    @Test
    void aFailedSendCanBeRetriedLater() {
        BambooReportNotifier notifier = notifier(properties(5));
        doThrow(new RuntimeException("smtp down")).when(sender).send(any(MimeMessage.class));

        notifier.notifyIfThresholdReached(MESSAGE_ID, 5);

        assertThat(notifier.pendingNotificationCount()).isZero();
    }

    @Test
    void theMailCarriesOnlyAPreviewOfTheContent() throws Exception {
        when(messages.findById(MESSAGE_ID)).thenReturn(Optional.of(new BambooMessage(7L, AUTHOR,
                "졸린사자42", "가".repeat(120), Instant.now(clock))));
        BambooAlertProperties shortPreview = new BambooAlertProperties(true, 1,
                "festa@example.com", "https://example.com", 10, 10);

        notifier(shortPreview).notifyIfThresholdReached(MESSAGE_ID, 1);

        ArgumentCaptor<MimeMessage> captured = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(captured.capture());
        assertThat(captured.getValue().getSubject()).isEqualTo("[2026 천보축전] 오픈채팅 이용 경고");
        assertThat(captured.getValue().getAllRecipients()).hasSize(1);
        assertThat(captured.getValue().getAllRecipients()[0].toString()).isEqualTo("author@example.com");
        captured.getValue().saveChanges();
        String body = partText(captured.getValue(), "text/plain");
        assertThat(body).doesNotContain("/admin");
        assertThat(body).contains("sahmyook.univ@likelion.org");
        assertThat(body).contains("가".repeat(10) + "…");
        assertThat(body).doesNotContain("가".repeat(11));
        assertThat(body).contains("https://example.com");
        String html = partText(captured.getValue(), "text/html");
        assertThat(html).contains("cid:warning-banner", "가".repeat(10) + "…", "문의", "이용약관");
        assertThat(html).doesNotContain("/admin", "가입 완료");
        assertThat(hasBanner(captured.getValue())).isTrue();
    }

    @Test
    void escapesUserContentInHtmlWarning() throws Exception {
        when(messages.findById(MESSAGE_ID)).thenReturn(Optional.of(new BambooMessage(7L, AUTHOR,
                "<b>name</b>", "<script>alert(1)</script>", Instant.now(clock))));
        notifier(properties(5)).notifyIfThresholdReached(MESSAGE_ID, 5);
        var captured = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(captured.capture());
        captured.getValue().saveChanges();
        String html = partText(captured.getValue(), "text/html");
        assertThat(html).contains("&lt;script&gt;", "&lt;b&gt;name&lt;/b&gt;");
        assertThat(html).doesNotContain("<script>", "<b>name</b>");
    }

    private String partText(jakarta.mail.Part part, String type) throws Exception {
        if (part.isMimeType(type)) return (String) part.getContent();
        if (part.isMimeType("multipart/*")) {
            var multipart = (jakarta.mail.Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                String result = partText(multipart.getBodyPart(i), type);
                if (!result.isEmpty()) return result;
            }
        }
        return "";
    }

    private boolean hasBanner(jakarta.mail.Part part) throws Exception {
        if (part.isMimeType("image/png")) {
            return java.util.Arrays.asList(part.getHeader("Content-ID")).contains("<warning-banner>");
        }
        if (part.isMimeType("multipart/*")) {
            var multipart = (jakarta.mail.Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                if (hasBanner(multipart.getBodyPart(i))) return true;
            }
        }
        return false;
    }

    private InternalUserProfile profile(String email) {
        return new InternalUserProfile(AUTHOR, "author", email, "USER", "ACTIVE", "Author",
                null, null, null, null, null, null, null, null);
    }

    @Test
    void profileFailureDoesNotFailReportAndAllowsLaterRetry() {
        when(profiles.getProfile(AUTHOR)).thenThrow(new RuntimeException("SSO unavailable"));
        var notifier = notifier(properties(5));
        notifier.notifyIfThresholdReached(MESSAGE_ID, 5);
        verify(sender, never()).send(any(MimeMessage.class));
        assertThat(notifier.pendingNotificationCount()).isZero();
    }

    private void stubMessage(long id) {
        when(messages.findById(id)).thenReturn(Optional.of(
                new BambooMessage(id, AUTHOR, "졸린사자42", "메시지 " + id, Instant.now(clock))));
    }

    private BambooAlertProperties properties(int threshold) {
        return new BambooAlertProperties(true, threshold, "festa@example.com",
                "https://example.com", 10, 50);
    }

    private BambooReportNotifier notifier(BambooAlertProperties properties) {
        return new BambooReportNotifier(messages, profiles, senders, DIRECT, properties, clock, templates);
    }
}
