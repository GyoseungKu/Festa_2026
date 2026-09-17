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
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.syu_likelion.Festa_2026.user.FestivalUserService;

@Service
public class WelcomeEmailService {
    private static final String SUPPORT_EMAIL = "sahmyook.univ@likelion.org";
    private static final String TERMS_URL = "https://festa.syu-likelion.org/terms/service";
    private static final String PRIVACY_URL = "https://festa.syu-likelion.org/terms/privacy";
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
            context.setVariable("supportEmail", SUPPORT_EMAIL);
            context.setVariable("termsUrl", TERMS_URL);
            context.setVariable("privacyUrl", PRIVACY_URL);
            helper.setText(plainText(name), templates.process("mail/welcome", context));
            helper.addInline("welcome-banner", new ClassPathResource("static/images/email-banner.png"), "image/png");
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
        return name + "님, 반가워요!\n\n"
                + "천보축전을 즐길 준비가 되셨나요?\n\n"
                + "삼육대학교 개교 120주년을 기념하는\n"
                + "2026 천보축전 홈페이지에 오신 것을 환영합니다.\n\n"
                + "여러분의 바람과 설렘이 모여\n"
                + "오래도록 기억될 특별한 축제가 되길 바랍니다.\n\n"
                + "천보축전 홈페이지 바로가기\n"
                + properties.siteUrl() + "\n\n"
                + "이용 중 궁금한 점이 있으신가요? "
                + SUPPORT_EMAIL + " 메일로 문의해 주세요.\n\n"
                + "본 메일은 천보축전 홈페이지 가입 완료에 따라 자동 발송되었습니다. "
                + "본인이 가입하지 않았다면 위 문의처로 알려 주세요.\n\n"
                + "멋쟁이사자처럼 삼육대학교 14기 · 대표자: 구교승\n"
                + "주소: 서울특별시 노원구 화랑로 815, 삼육대학교 학생회관 304호\n"
                + "https://syu-likelion.org\n"
                + "이용약관: " + TERMS_URL + "\n"
                + "개인정보처리방침: " + PRIVACY_URL + "\n"
                + "호스팅 서비스 제공: Oracle Cloud Infrastructure (OCI), Cloudflare\n"
                + "© Likelion SYU. All rights reserved.";
    }
}
