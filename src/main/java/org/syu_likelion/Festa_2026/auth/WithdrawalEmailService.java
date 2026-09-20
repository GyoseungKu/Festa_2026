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

@Service
public class WithdrawalEmailService {
    public enum Kind { FESTIVAL, SSO }

    private static final Logger log = LoggerFactory.getLogger(WithdrawalEmailService.class);
    private static final String SUPPORT_EMAIL = "sahmyook.univ@likelion.org";
    private static final String TERMS_URL = "https://festa.syu-likelion.org/terms/service";
    private static final String PRIVACY_URL = "https://festa.syu-likelion.org/terms/privacy";
    private final ObjectProvider<JavaMailSender> mailSenders;
    private final SpringTemplateEngine templates;
    private final Executor executor;
    private final WithdrawalEmailProperties properties;

    public WithdrawalEmailService(ObjectProvider<JavaMailSender> mailSenders, SpringTemplateEngine templates,
                                  @Qualifier("welcomeEmailExecutor") Executor executor,
                                  WithdrawalEmailProperties properties) {
        this.mailSenders = mailSenders;
        this.templates = templates;
        this.executor = executor;
        this.properties = properties;
    }

    /** Called after successful withdrawal. Notification failures must not turn it into a failed API request. */
    public void sendLater(UUID userUuid, String email, String name, Kind kind) {
        if (!properties.enabled() || email == null || email.isBlank()) return;
        try {
            JavaMailSender sender = mailSenders.getIfAvailable();
            if (sender == null) {
                log.warn("Withdrawal email skipped reason=mail_sender_unavailable userUuid={} kind={}", userUuid, kind);
                return;
            }
            String displayName = name == null || name.isBlank() ? "천보축전 방문자" : name.trim();
            executor.execute(() -> send(sender, userUuid, email.trim(), displayName, kind));
        } catch (RuntimeException failure) {
            log.warn("Withdrawal email queue failed userUuid={} kind={}", userUuid, kind, failure);
        }
    }

    private void send(JavaMailSender sender, UUID userUuid, String email, String name, Kind kind) {
        try {
            String title = kind == Kind.FESTIVAL ? "천보축전 홈페이지 탈퇴가 완료되었습니다" : "천보축전 홈페이지 및 통합 SSO 계정 탈퇴가 완료되었습니다";
            String accountNotice = kind == Kind.FESTIVAL
                    ? "멋쟁이사자처럼 통합 SSO 계정은 그대로 유지됩니다. 다시 이용하고 싶으시면 별도로 가입하지 않고 기존 SSO 계정으로 로그인하시면 됩니다."
                    : "멋쟁이사자처럼 통합 SSO 계정도 탈퇴 처리되었습니다. 기존 계정으로 서비스를 계속 이용할 수 없으며, 다시 이용하려면 SSO의 계정 복구 또는 재가입 절차를 따라 주세요.";
            String dataNotice = "축제 홈페이지 이용 정보는 삭제하거나 익명화했습니다. 게시글 본문과 일부 운영 기록은 보존되며, 삭제·익명화된 활동 내역과 기존 권한·학생 인증은 다시 로그인하거나 계정을 복구해도 복원되지 않습니다.";
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.from(), properties.fromName());
            helper.setTo(email);
            helper.setSubject(kind == Kind.FESTIVAL ? "[2026 천보축전] 홈페이지 탈퇴 완료 안내" : "[2026 천보축전] 홈페이지 및 통합 SSO 탈퇴 완료 안내");
            Context context = new Context(Locale.KOREA);
            context.setVariable("name", name);
            context.setVariable("title", title);
            context.setVariable("accountNotice", accountNotice);
            context.setVariable("dataNotice", dataNotice);
            context.setVariable("siteUrl", properties.siteUrl());
            context.setVariable("supportEmail", SUPPORT_EMAIL);
            context.setVariable("termsUrl", TERMS_URL);
            context.setVariable("privacyUrl", PRIVACY_URL);
            helper.setText(plainText(name, title, accountNotice, dataNotice), templates.process("mail/withdrawal", context));
            helper.addInline("withdrawal-banner", new ClassPathResource("static/images/email-banner.png"), "image/png");
            sender.send(message);
            log.info("Withdrawal email sent userUuid={} kind={}", userUuid, kind);
        } catch (Exception failure) {
            log.warn("Withdrawal email failed userUuid={} kind={}", userUuid, kind, failure);
        }
    }

    private String plainText(String name, String title, String accountNotice, String dataNotice) {
        return name + "님, " + title + ".\n\n" + accountNotice + "\n\n" + dataNotice
                + "\n\n그동안 2026 천보축전 홈페이지를 이용해 주셔서 감사합니다.\n"
                + properties.siteUrl() + "\n\n"
                + "본 메일은 회원탈퇴 완료에 따라 자동 발송되었습니다. 본인이 요청하지 않았다면 " + SUPPORT_EMAIL + " 메일로 문의해 주세요.\n\n"
                + "멋쟁이사자처럼 삼육대학교 14기 · 대표자: 구교승\n"
                + "주소: 서울특별시 노원구 화랑로 815, 삼육대학교 학생회관 304호\n"
                + "https://syu-likelion.org\n이용약관: " + TERMS_URL + "\n개인정보처리방침: " + PRIVACY_URL + "\n"
                + "호스팅 서비스 제공: Oracle Cloud Infrastructure (OCI), Cloudflare\n"
                + "© Likelion SYU. All rights reserved.";
    }
}
