package org.syu_likelion.Festa_2026.auth;

import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.syu_likelion.Festa_2026.user.FestivalUserService;

@Service
public class WelcomeEmailService {
    private static final Logger log = LoggerFactory.getLogger(WelcomeEmailService.class);
    private final FestivalUserService festivalUsers;
    private final ObjectProvider<JavaMailSender> mailSenders;
    private final SpringTemplateEngine templates;
    private final Executor executor;
    private final WelcomeEmailProperties properties;

    public WelcomeEmailService(FestivalUserService festivalUsers,
                               ObjectProvider<JavaMailSender> mailSenders,
                               SpringTemplateEngine templates,
                               @Qualifier("welcomeEmailExecutor") Executor executor,
                               WelcomeEmailProperties properties) {
        this.festivalUsers = festivalUsers;
        this.mailSenders = mailSenders;
        this.templates = templates;
        this.executor = executor;
        this.properties = properties;
    }

    public void sendLater(UUID userUuid, String email, String name) {
        if (!properties.enabled() || userUuid == null || email == null || email.isBlank()) return;
        JavaMailSender sender = mailSenders.getIfAvailable();
        if (sender == null) {
            log.warn("Welcome email skipped reason=mail_sender_unavailable userUuid={}", userUuid);
            return;
        }
        UUID claimToken = festivalUsers.claimWelcomeEmail(userUuid);
        if (claimToken == null) return;
        try {
            executor.execute(() -> sendClaimed(sender, userUuid, claimToken, email.trim(), displayName(name)));
        } catch (RuntimeException rejected) {
            festivalUsers.releaseWelcomeEmailClaim(userUuid, claimToken);
            log.warn("Welcome email queue rejected userUuid={}", userUuid, rejected);
        }
    }

    private void sendClaimed(JavaMailSender sender, UUID userUuid, UUID claimToken,
                             String email, String name) {
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.from(), properties.fromName());
            helper.setTo(email);
            helper.setSubject(properties.subject());
            Context context = new Context(Locale.KOREA);
            context.setVariable("name", name);
            context.setVariable("siteUrl", properties.siteUrl());
            helper.setText(plainText(name), templates.process("mail/welcome", context));
            sender.send(message);
            festivalUsers.completeWelcomeEmail(userUuid, claimToken);
            log.info("Welcome email sent userUuid={} success=true", userUuid);
        } catch (Exception failure) {
            festivalUsers.releaseWelcomeEmailClaim(userUuid, claimToken);
            log.warn("Welcome email failed userUuid={} success=false", userUuid, failure);
        }
    }

    private String displayName(String name) {
        return name == null || name.isBlank() ? "천보축전 방문자" : name.trim();
    }

    private String plainText(String name) {
        return name + "님, 삼육대학교 개교 120주년 천보축전 홈페이지 가입을 환영합니다.\n\n"
                + "부스 지도, 공연 일정, 스탬프와 투표 등 축제의 다양한 기능을 이용해 보세요.\n"
                + properties.siteUrl();
    }
}
