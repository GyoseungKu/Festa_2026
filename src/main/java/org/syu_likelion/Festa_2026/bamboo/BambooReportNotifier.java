package org.syu_likelion.Festa_2026.bamboo;

import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.Locale;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.core.io.ClassPathResource;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.syu_likelion.Festa_2026.sso.SsoInternalProfileClient;

/**
 * 신고가 일정 수 이상 쌓이면 작성자의 SSO 이메일로 이용 주의 메일을 보낸다.
 * 신고자 정보는 포함하지 않으며 메일 발송은 메시지 상태를 변경하지 않는다.
 */
@Service
public class BambooReportNotifier {
    private static final Logger log = LoggerFactory.getLogger(BambooReportNotifier.class);
    private static final Duration MINUTE = Duration.ofMinutes(1);

    /** 메시지당 한 번만 알린다. 재시작하면 비므로 최악의 경우 중복 한 통이다. */
    private final Set<Long> notified = ConcurrentHashMap.newKeySet();
    private final BambooMessageRepository messages;
    private final SsoInternalProfileClient profiles;
    private final ObjectProvider<JavaMailSender> mailSenders;
    private final Executor executor;
    private final BambooAlertProperties properties;
    private final Clock clock;
    private final SpringTemplateEngine templates;

    private Instant windowStart;
    private int sentInWindow;

    public BambooReportNotifier(BambooMessageRepository messages, SsoInternalProfileClient profiles,
                                ObjectProvider<JavaMailSender> mailSenders,
                                @Qualifier("bambooAlertExecutor") Executor executor,
                                BambooAlertProperties properties, Clock clock, SpringTemplateEngine templates) {
        this.messages = messages;
        this.profiles = profiles;
        this.mailSenders = mailSenders;
        this.executor = executor;
        this.properties = properties;
        this.clock = clock;
        this.templates = templates;
        this.windowStart = Instant.now(clock);
    }

    public void notifyIfThresholdReached(Long messageId, long reportCount) {
        if (!properties.deliverable() || messageId == null) return;
        if (reportCount < properties.threshold()) return;
        if (!notified.add(messageId)) return;
        JavaMailSender sender = mailSenders.getIfAvailable();
        if (sender == null) {
            notified.remove(messageId);
            log.warn("Bamboo report alert skipped reason=mail_sender_unavailable messageId={}", messageId);
            return;
        }
        if (!withinSendingBudget()) {
            notified.remove(messageId);
            log.warn("Bamboo report alert skipped reason=rate_limited messageId={}", messageId);
            return;
        }
        try {
            executor.execute(() -> send(sender, messageId, reportCount));
        } catch (RuntimeException rejected) {
            notified.remove(messageId);
            log.warn("Bamboo report alert queue rejected messageId={}", messageId, rejected);
        }
    }

    /**
     * 여러 메시지가 동시에 임계값에 닿으면 메일이 쏟아진다. SMTP 계정의 일일 한도를 넘기면
     * 가입 환영 메일까지 함께 막히므로 분당 상한을 둔다.
     */
    private synchronized boolean withinSendingBudget() {
        Instant now = Instant.now(clock);
        if (!now.isBefore(windowStart.plus(MINUTE))) {
            windowStart = now;
            sentInWindow = 0;
        }
        if (sentInWindow >= properties.maxPerMinute()) return false;
        sentInWindow++;
        return true;
    }

    private void send(JavaMailSender sender, Long messageId, long reportCount) {
        try {
            BambooMessage message = messages.findById(messageId).orElse(null);
            if (message == null) {
                notified.remove(messageId);
                return;
            }
            var profile = profiles.getProfile(message.getUserUuid());
            if (profile == null || !message.getUserUuid().equals(profile.userUuid())
                    || profile.email() == null || profile.email().isBlank()) {
                notified.remove(messageId);
                log.info("Bamboo report alert skipped reason=author_email_unavailable messageId={}", messageId);
                return;
            }
            MimeMessage mail = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mail, true, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.from());
            helper.setTo(profile.email());
            helper.setSubject("[2026 천보축전] 오픈채팅 이용 경고");
            Context context = new Context(Locale.KOREA);
            context.setVariable("nickname", message.getAnonName());
            context.setVariable("messageId", message.getId());
            context.setVariable("reportCount", reportCount);
            context.setVariable("createdAt", formattedTime(message));
            context.setVariable("preview", preview(message.getContent()));
            context.setVariable("siteUrl", properties.serviceUrl());
            helper.setText(body(message, reportCount), templates.process("mail/bamboo-warning", context));
            helper.addInline("warning-banner", new ClassPathResource("static/images/email-banner.png"), "image/png");
            sender.send(mail);
            log.info("Bamboo report alert sent messageId={} reportCount={} success=true",
                    messageId, reportCount);
        } catch (Exception failure) {
            notified.remove(messageId);
            log.warn("Bamboo report alert failed messageId={} success=false", messageId, failure);
        }
    }

    private String body(BambooMessage message, long reportCount) {
        return "회원님이 작성한 대나무숲 메시지에 신고가 " + reportCount + "건 누적되어 이용 주의를 안내드립니다.\n\n"
                + "메시지 번호: " + message.getId() + "\n"
                + "작성 닉네임: " + message.getAnonName() + "\n"
                + "작성 시각: " + formattedTime(message) + "\n"
                + "내용 일부: " + preview(message.getContent()) + "\n\n"
                + "서비스 이용약관을 준수해 주시기 바랍니다. 위반 사항이 확인되면 운영정책에 따라 게시물 차단 또는 이용 제한이 적용될 수 있습니다.\n"
                + "신고가 누적되었다는 사실만으로 약관 위반이 확정되는 것은 아닙니다.\n\n"
                + "천보축전 홈페이지: " + properties.serviceUrl() + "\n"
                + "문의·이의신청: sahmyook.univ@likelion.org\n\n"
                + "이 메일은 신고 누적에 따라 자동 발송된 발신 전용 메일입니다.\n\n"
                + "멋쟁이사자처럼 삼육대학교 14기 · 대표자: 구교승\n"
                + "주소: 서울특별시 노원구 화랑로 815, 삼육대학교 학생회관 304호\n"
                + "https://syu-likelion.org\n"
                + "이용약관: https://festa.syu-likelion.org/terms/service\n"
                + "개인정보처리방침: https://festa.syu-likelion.org/terms/privacy\n"
                + "호스팅 서비스 제공: Oracle Cloud Infrastructure (OCI), Cloudflare\n"
                + "© Likelion SYU. All rights reserved.";
    }

    private String formattedTime(BambooMessage message) {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'KST'")
                .withZone(ZoneId.of("Asia/Seoul")).format(message.getCreatedAt());
    }

    private String preview(String content) {
        String flattened = content.replace('\n', ' ').strip();
        int limit = properties.previewLength();
        if (flattened.codePointCount(0, flattened.length()) <= limit) return flattened;
        return flattened.substring(0, flattened.offsetByCodePoints(0, limit)) + "…";
    }

    int pendingNotificationCount() { return notified.size(); }
}
