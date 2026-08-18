package org.syu_likelion.Festa_2026.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.syu_likelion.Festa_2026.user.FestivalUserService;

class WelcomeEmailServiceTests {
    private static final UUID USER_UUID = UUID.fromString("123e4567-e89b-12d3-a456-426614174401");
    private static final UUID CLAIM = UUID.fromString("123e4567-e89b-12d3-a456-426614174402");
    private FestivalUserService users;
    private JavaMailSender sender;
    private SpringTemplateEngine templates;
    private WelcomeEmailService service;
    private MimeMessage message;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        users = mock(FestivalUserService.class);
        sender = mock(JavaMailSender.class);
        templates = mock(SpringTemplateEngine.class);
        ObjectProvider<JavaMailSender> providers = mock(ObjectProvider.class);
        when(providers.getIfAvailable()).thenReturn(sender);
        message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        when(templates.process(eq("mail/welcome"), any(Context.class))).thenReturn("<html><body>환영합니다</body></html>");
        Executor direct = Runnable::run;
        service = new WelcomeEmailService(users, providers, templates, direct,
                new WelcomeEmailProperties(true, "no-reply@syu-likelion.org", "Likelion SYU",
                        "[Likelion SYU] 삼육대학교 개교 120주년 천보축전 홈페이지 가입을 환영합니다.",
                        "https://festa.syu-likelion.org"));
    }

    @Test
    void sendsHtmlWelcomeEmailAndCompletesClaim() throws Exception {
        when(users.claimWelcomeEmail(USER_UUID)).thenReturn(CLAIM);

        service.sendLater(USER_UUID, "student@example.com", "홍길동");

        verify(sender).send(message);
        verify(templates).process(eq("mail/welcome"), any(Context.class));
        verify(users).completeWelcomeEmail(USER_UUID, CLAIM);
        assertThat(message.getSubject()).contains("천보축전 홈페이지 가입을 환영합니다");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("student@example.com");
    }

    @Test
    void releasesClaimWhenSmtpDeliveryFails() {
        when(users.claimWelcomeEmail(USER_UUID)).thenReturn(CLAIM);
        doThrow(new MailSendException("smtp unavailable")).when(sender).send(any(MimeMessage.class));

        service.sendLater(USER_UUID, "student@example.com", "홍길동");

        verify(users).releaseWelcomeEmailClaim(USER_UUID, CLAIM);
        verify(users, never()).completeWelcomeEmail(USER_UUID, CLAIM);
    }

    @Test
    void doesNothingWhenWelcomeEmailWasAlreadyClaimedOrSent() {
        when(users.claimWelcomeEmail(USER_UUID)).thenReturn(null);

        service.sendLater(USER_UUID, "student@example.com", "홍길동");

        verify(sender, never()).send(any(MimeMessage.class));
    }
}
