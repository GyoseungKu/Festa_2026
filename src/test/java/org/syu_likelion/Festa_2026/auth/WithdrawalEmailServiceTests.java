package org.syu_likelion.Festa_2026.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.syu_likelion.Festa_2026.auth.WithdrawalEmailService.Kind;

class WithdrawalEmailServiceTests {
    private JavaMailSender sender;
    private ObjectProvider<JavaMailSender> providers;
    private SpringTemplateEngine templates;
    private MimeMessage message;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        sender = mock(JavaMailSender.class);
        providers = mock(ObjectProvider.class);
        when(providers.getIfAvailable()).thenReturn(sender);
        message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        templates = new SpringTemplateEngine();
        templates.setTemplateResolver(resolver);
    }

    private WithdrawalEmailService service(boolean enabled, Executor executor) {
        return new WithdrawalEmailService(providers, templates, executor,
                new WithdrawalEmailProperties(enabled, "no-reply@syu-likelion.org", "Likelion SYU",
                        "https://festa.syu-likelion.org"));
    }

    @Test void festivalMailExplainsRetainedSsoInHtmlAndPlainText() throws Exception {
        service(true, Runnable::run).sendLater(UUID.randomUUID(), "student@example.com", "<script>name</script>", Kind.FESTIVAL);
        verify(sender).send(message);
        message.saveChanges();
        assertThat(message.getSubject()).isEqualTo("[2026 천보축전] 홈페이지 탈퇴 완료 안내");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("student@example.com");
        String html = body(message, "text/html");
        String text = body(message, "text/plain");
        for (String content : new String[]{html, text}) {
            assertThat(content).contains("통합 SSO 계정은 그대로 유지", "별도로 가입하지 않고", "복원되지 않습니다", "sahmyook.univ@likelion.org");
            assertThat(content).doesNotContain("통합 SSO 계정도 탈퇴 처리");
        }
        assertThat(html).contains("cid:withdrawal-banner", "&lt;script&gt;name&lt;/script&gt;", "개인정보처리방침").doesNotContain("<script>");
        assertThat(hasBanner(message)).isTrue();
    }

    @Test void ssoMailExplainsBothWithdrawalsWithoutPromisingImmediateLogin() throws Exception {
        service(true, Runnable::run).sendLater(UUID.randomUUID(), "student@example.com", null, Kind.SSO);
        verify(sender).send(message);
        message.saveChanges();
        assertThat(message.getSubject()).contains("홈페이지 및 통합 SSO 탈퇴");
        for (String content : new String[]{body(message, "text/html"), body(message, "text/plain")}) {
            assertThat(content).contains("통합 SSO 계정도 탈퇴 처리", "계정 복구 또는 재가입", "천보축전 방문자");
            assertThat(content).doesNotContain("계정은 그대로 유지", "별도로 가입하지 않고");
        }
    }

    @Test void smtpAndQueueFailuresDoNotEscape() {
        doThrow(new MailSendException("unavailable")).when(sender).send(any(MimeMessage.class));
        assertThatCode(() -> service(true, Runnable::run).sendLater(UUID.randomUUID(), "student@example.com", "이름", Kind.SSO))
                .doesNotThrowAnyException();
        assertThatCode(() -> service(true, task -> { throw new java.util.concurrent.RejectedExecutionException(); })
                .sendLater(UUID.randomUUID(), "student@example.com", "이름", Kind.FESTIVAL)).doesNotThrowAnyException();
    }

    @Test void disabledOrMissingRecipientDoesNotQueueMail() {
        Executor executor = mock(Executor.class);
        service(false, executor).sendLater(UUID.randomUUID(), "student@example.com", "이름", Kind.SSO);
        service(true, executor).sendLater(UUID.randomUUID(), null, "이름", Kind.FESTIVAL);
        service(true, executor).sendLater(UUID.randomUUID(), " ", "이름", Kind.FESTIVAL);
        verifyNoInteractions(executor, sender, providers);
    }

    @Test void missingSenderDoesNotFailWithdrawal() {
        when(providers.getIfAvailable()).thenReturn(null);
        Executor executor = mock(Executor.class);
        assertThatCode(() -> service(true, executor).sendLater(UUID.randomUUID(), "student@example.com", "이름", Kind.FESTIVAL))
                .doesNotThrowAnyException();
        verifyNoInteractions(executor);
    }

    private static String body(Part part, String type) throws Exception {
        if (part.isMimeType(type)) return (String) part.getContent();
        if (part.getContent() instanceof Multipart multipart) {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) text.append(body(multipart.getBodyPart(i), type));
            return text.toString();
        }
        return "";
    }

    private static boolean hasBanner(Part part) throws Exception {
        String[] ids = part.getHeader("Content-ID");
        if (ids != null && java.util.Arrays.asList(ids).contains("<withdrawal-banner>")) return part.isMimeType("image/png");
        if (part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) if (hasBanner(multipart.getBodyPart(i))) return true;
        }
        return false;
    }
}
