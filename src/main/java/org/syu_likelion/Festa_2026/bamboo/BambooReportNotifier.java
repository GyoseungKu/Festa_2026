package org.syu_likelion.Festa_2026.bamboo;

import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * 신고가 일정 수 이상 쌓인 메시지를 관리자에게 알린다.
 *
 * <p>메시지를 자동으로 가리지는 않는다. 차단 기준은 합의가 필요한 사안이라 여기서는
 * 사람이 볼 수 있게 알리기만 한다. 축제 전 감시 인력이 없는 기간을 메우는 것이 목적이다.
 *
 * <p>메일 본문에는 내용 앞부분만 넣는다. 신고된 글은 문제 소지가 있는 텍스트이고
 * 메일함에는 영구히 남으므로, 판단은 관리자 화면에서 하게 한다.
 */
@Service
public class BambooReportNotifier {
    private static final Logger log = LoggerFactory.getLogger(BambooReportNotifier.class);
    private static final Duration MINUTE = Duration.ofMinutes(1);

    /** 메시지당 한 번만 알린다. 재시작하면 비므로 최악의 경우 중복 한 통이다. */
    private final Set<Long> notified = ConcurrentHashMap.newKeySet();
    private final BambooMessageRepository messages;
    private final ObjectProvider<JavaMailSender> mailSenders;
    private final Executor executor;
    private final BambooAlertProperties properties;
    private final Clock clock;

    private Instant windowStart;
    private int sentInWindow;

    public BambooReportNotifier(BambooMessageRepository messages,
                                ObjectProvider<JavaMailSender> mailSenders,
                                @Qualifier("bambooAlertExecutor") Executor executor,
                                BambooAlertProperties properties, Clock clock) {
        this.messages = messages;
        this.mailSenders = mailSenders;
        this.executor = executor;
        this.properties = properties;
        this.clock = clock;
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
            MimeMessage mail = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mail, false, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.from());
            helper.setTo(properties.to().toArray(String[]::new));
            helper.setSubject("[2026 천보축전] 오픈채팅 이용 경고");
            helper.setText(body(message, reportCount));
            sender.send(mail);
            log.info("Bamboo report alert sent messageId={} reportCount={} success=true",
                    messageId, reportCount);
        } catch (Exception failure) {
            notified.remove(messageId);
            log.warn("Bamboo report alert failed messageId={} success=false", messageId, failure);
        }
    }

    private String body(BambooMessage message, long reportCount) {
        return "대나무숲 메시지에 신고가 " + reportCount + "건 누적되었습니다.\n\n"
                + "메시지 번호: " + message.getId() + "\n"
                + "작성 닉네임: " + message.getAnonName() + "\n"
                + "작성 시각: " + message.getCreatedAt() + "\n"
                + "내용 일부: " + preview(message.getContent()) + "\n\n"
                + "전체 내용 확인과 처리는 관리자 페이지에서 진행해 주세요.\n"
                + properties.adminUrl() + "\n\n"
                + "이 메일은 알림일 뿐이며 메시지는 자동으로 가려지지 않았습니다.";
    }

    private String preview(String content) {
        String flattened = content.replace('\n', ' ').strip();
        int limit = properties.previewLength();
        if (flattened.codePointCount(0, flattened.length()) <= limit) return flattened;
        return flattened.substring(0, flattened.offsetByCodePoints(0, limit)) + "…";
    }

    int pendingNotificationCount() { return notified.size(); }
}
