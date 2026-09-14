package org.syu_likelion.Festa_2026.bamboo;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.syu_likelion.Festa_2026.bamboo.BambooDtos.BambooAdminMessageResponse;
import org.syu_likelion.Festa_2026.bamboo.BambooDtos.BambooAdminPageResponse;
import org.syu_likelion.Festa_2026.bamboo.BambooDtos.BambooMessageResponse;
import org.syu_likelion.Festa_2026.bamboo.BambooDtos.BambooModerationAuditPageResponse;
import org.syu_likelion.Festa_2026.bamboo.BambooDtos.BambooModerationAuditResponse;
import org.syu_likelion.Festa_2026.bamboo.BambooDtos.BambooMuteResponse;
import org.syu_likelion.Festa_2026.bamboo.BambooDtos.BambooParticipantPageResponse;
import org.syu_likelion.Festa_2026.bamboo.BambooDtos.BambooParticipantResponse;
import org.syu_likelion.Festa_2026.bamboo.BambooDtos.BambooSettingsResponse;
import org.syu_likelion.Festa_2026.bamboo.BambooDtos.BambooNicknameResponse;
import org.syu_likelion.Festa_2026.bamboo.BambooDtos.BambooRoomResponse;
import org.syu_likelion.Festa_2026.bamboo.BambooDtos.BambooStreamResponse;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.user.FestivalRole;

/**
 * 대나무숲 도메인 서비스. SSO를 알지 못하고 {@code userUuid}만 받는다.
 */
@Service
public class BambooService {
    static final int MAX_CONTENT_CODE_POINTS = 200;
    static final int MAX_LINE_BREAKS = 5;
    private static final int DEFAULT_HISTORY_SIZE = 50;
    private static final int MAX_HISTORY_SIZE = 100;
    private static final int MAX_STREAM_SIZE = 200;
    private static final int NICKNAME_SUGGEST_ATTEMPTS = 10;

    private final BambooMessageRepository messages;
    private final BambooNicknameRepository nicknames;
    private final BambooSettingsRepository settings;
    private final BambooReportRepository reports;
    private final BambooModerationAuditRepository moderationAudits;
    private final BambooSequence sequence;
    private final BambooRateLimiter rateLimiter;
    private final Clock clock;
    private final Object settingsInitializationLock = new Object();
    /** 설정에 적힌 금칙어를 닉네임과 같은 방식으로 정규화해 둔다. 매 요청마다 다시 만들지 않는다. */
    private final Set<String> blockedWords;

    public BambooService(BambooMessageRepository messages, BambooNicknameRepository nicknames,
                         BambooSettingsRepository settings, BambooReportRepository reports,
                         BambooModerationAuditRepository moderationAudits,
                         BambooSequence sequence, BambooRateLimiter rateLimiter,
                         BambooProperties properties, Clock clock) {
        this.messages = messages;
        this.nicknames = nicknames;
        this.settings = settings;
        this.reports = reports;
        this.moderationAudits = moderationAudits;
        this.sequence = sequence;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
        this.blockedWords = properties.blockedWords().stream()
                .map(BambooNicknamePolicy::normalizeText)
                .filter(word -> !word.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    // ------------------------------------------------------------------ 방 상태

    public BambooRoomResponse room(UUID viewerUuid) {
        Instant now = Instant.now(clock);
        BambooSettings current = currentSettings();
        String nickname = nicknames.findById(viewerUuid).map(BambooNickname::getNickname).orElse(null);
        return new BambooRoomResponse(current.isEnabled(), current.isReadOnlyAt(now), current.getClosesAt(),
                nickname, sequence.current());
    }

    // ------------------------------------------------------------------ 닉네임

    @Transactional(readOnly = true)
    public BambooNicknameResponse suggestNickname() {
        for (int attempt = 0; attempt < NICKNAME_SUGGEST_ATTEMPTS; attempt++) {
            String candidate = BambooNicknamePolicy.randomCandidate();
            if (!nicknames.existsByNicknameKey(BambooNicknamePolicy.normalize(candidate))) {
                return new BambooNicknameResponse(candidate);
            }
        }
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "BAMBOO_NICKNAME_UNAVAILABLE",
                "사용할 수 있는 닉네임을 찾지 못했습니다. 직접 입력해 주세요.");
    }

    /**
     * 트랜잭션을 걸지 않는다. 리포지토리 호출이 각자 트랜잭션을 열고 닫아야
     * 유니크 위반이 바깥 커밋 시점이 아니라 저장 호출 지점에서 드러난다.
     */
    public BambooNicknameResponse claimNickname(UUID userUuid, String requested) {
        requireEnabled();
        if (nicknames.existsById(userUuid)) {
            throw new ApiException(HttpStatus.CONFLICT, "BAMBOO_NICKNAME_ALREADY_SET",
                    "닉네임은 한 번만 정할 수 있습니다.");
        }
        String nickname = requested == null ? "" : requested.strip();
        validateNickname(nickname);
        String key = BambooNicknamePolicy.normalize(nickname);
        if (key.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAMBOO_NICKNAME_INVALID",
                    "사용할 수 없는 닉네임입니다.");
        }
        if (BambooNicknamePolicy.isBlocked(key)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAMBOO_NICKNAME_BLOCKED",
                    "사용할 수 없는 닉네임입니다.");
        }
        // 정상 경로는 여기서 걸러 409 로 끝낸다. 아래 catch 는 동시 신청 경합용 백스톱이다.
        if (nicknames.existsByNicknameKey(key)) {
            throw new ApiException(HttpStatus.CONFLICT, "BAMBOO_NICKNAME_TAKEN",
                    "이미 사용 중인 닉네임입니다. 다른 닉네임을 입력해 주세요.");
        }
        try {
            nicknames.saveAndFlush(new BambooNickname(userUuid, nickname, key, Instant.now(clock)));
        } catch (DataIntegrityViolationException conflict) {
            if (nicknames.existsById(userUuid)) {
                throw new ApiException(HttpStatus.CONFLICT, "BAMBOO_NICKNAME_ALREADY_SET",
                        "닉네임은 한 번만 정할 수 있습니다.");
            }
            throw new ApiException(HttpStatus.CONFLICT, "BAMBOO_NICKNAME_TAKEN",
                    "이미 사용 중인 닉네임입니다. 다른 닉네임을 입력해 주세요.");
        }
        return new BambooNicknameResponse(nickname);
    }

    private void validateNickname(String nickname) {
        int length = BambooNicknamePolicy.length(nickname);
        if (length < BambooNicknamePolicy.MIN_LENGTH || length > BambooNicknamePolicy.MAX_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAMBOO_NICKNAME_INVALID",
                    "닉네임은 " + BambooNicknamePolicy.MIN_LENGTH + "자 이상 "
                            + BambooNicknamePolicy.MAX_LENGTH + "자 이하로 입력해 주세요.");
        }
        if (!BambooNicknamePolicy.hasOnlyAllowedCharacters(nickname)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAMBOO_NICKNAME_INVALID",
                    "닉네임에는 한글, 영문, 숫자와 _ - 및 중간 공백 하나만 사용할 수 있습니다.");
        }
    }

    // ------------------------------------------------------------------ 조회

    /**
     * 실시간 스트림. 신규 메시지와 상태 변경이 같은 커서로 흐른다.
     * {@code after}가 없으면 현재 커서를 기준으로 삼아 빈 결과를 돌려준다.
     */
    public BambooStreamResponse stream(UUID viewerUuid, Long after, Integer size) {
        requireEnabled();
        long cursor = after == null ? sequence.current() : Math.max(0, after);
        int safeSize = size == null ? MAX_STREAM_SIZE : Math.max(1, Math.min(size, MAX_STREAM_SIZE));
        List<BambooMessage> found = messages.findBySeqGreaterThanOrderBySeqAsc(cursor,
                PageRequest.of(0, safeSize));
        List<BambooMessageResponse> items = new ArrayList<>(found.size());
        long next = cursor;
        for (BambooMessage message : found) {
            items.add(toResponse(message, viewerUuid));
            next = Math.max(next, message.getSeq());
        }
        return new BambooStreamResponse(next, items);
    }

    /**
     * 과거 조회. 오래된 것이 위에 오도록 오름차순으로 뒤집어 반환한다.
     * {@code cursor}에는 현재 커서를 담아, 최초 진입에서 과거 목록과 스트림 시작점을 한 번에 받게 한다.
     */
    public BambooStreamResponse history(UUID viewerUuid, Long before, Integer size) {
        requireEnabled();
        long cursor = before == null ? Long.MAX_VALUE : before;
        int safeSize = size == null ? DEFAULT_HISTORY_SIZE : Math.max(1, Math.min(size, MAX_HISTORY_SIZE));
        return sequence.readSnapshot(() -> {
            List<BambooMessage> found = messages.findByIdLessThanAndStatusNotOrderByIdDesc(cursor,
                    BambooMessageStatus.DELETED, PageRequest.of(0, safeSize));
            List<BambooMessageResponse> items = new ArrayList<>(found.size());
            for (int index = found.size() - 1; index >= 0; index--) {
                items.add(toResponse(found.get(index), viewerUuid));
            }
            return new BambooStreamResponse(sequence.current(), items);
        });
    }

    // ------------------------------------------------------------------ 작성

    /**
     * 트랜잭션 애너테이션을 붙이지 않는다. 쓰기 트랜잭션은 {@link BambooSequence#writeInOrder}가
     * 임계 구역 안에서 직접 열고 닫아야 커밋 순서와 커서 순서가 일치한다.
     */
    public BambooMessageResponse createAs(UUID userUuid, String rawContent) {
        String content = normalizeContent(rawContent);
        requireAllowedContent(content);
        BambooMessage saved = sequence.writeInOrder(seq -> {
            Instant now = Instant.now(clock);
            BambooSettings current = currentSettings();
            requireEnabled(current);
            if (current.isReadOnlyAt(now)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "BAMBOO_READ_ONLY",
                        "지금은 새 메시지를 작성할 수 없습니다.");
            }
            BambooNickname nickname = nicknames.findById(userUuid).orElseThrow(() ->
                    new ApiException(HttpStatus.CONFLICT, "BAMBOO_NICKNAME_REQUIRED",
                            "먼저 닉네임을 정해 주세요."));
            requireNotMuted(nickname, now);
            rateLimiter.checkWrite(userUuid, content);
            return messages.save(new BambooMessage(seq, userUuid, nickname.getNickname(), content, now));
        });
        return toResponse(saved, userUuid);
    }

    /**
     * 작성 본문 정규화. 줄바꿈을 통일하고 연속 공백·개행을 압축한 뒤 길이를 검사한다.
     * 개행 반복으로 다른 메시지를 화면 밖으로 밀어내는 어뷰징을 막는다.
     */
    static String normalizeContent(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAMBOO_CONTENT_REQUIRED",
                    "메시지를 입력해 주세요.");
        }
        String unified = raw.replace("\r\n", "\n").replace('\r', '\n');
        String collapsed = unified
                .replaceAll("[^\\S\\n]+", " ")
                .replaceAll(" *\n *", "\n")
                .replaceAll("\n{3,}", "\n\n")
                .strip();
        if (collapsed.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAMBOO_CONTENT_REQUIRED",
                    "메시지를 입력해 주세요.");
        }
        if (collapsed.chars().filter(character -> character == '\n').count() > MAX_LINE_BREAKS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAMBOO_TOO_MANY_LINE_BREAKS",
                    "줄바꿈은 " + MAX_LINE_BREAKS + "개까지 사용할 수 있습니다.");
        }
        if (collapsed.codePointCount(0, collapsed.length()) > MAX_CONTENT_CODE_POINTS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAMBOO_CONTENT_TOO_LONG",
                    "메시지는 " + MAX_CONTENT_CODE_POINTS + "자까지 입력할 수 있습니다.");
        }
        return collapsed;
    }

    /**
     * 금칙어를 부분 문자열로 검사한다. 대소문자·전각·호모글리프만 접고 공백은 유지한다.
     *
     * <p>짧은 금칙어는 정상 단어를 오탐한다("시발" ⊂ "시발점"). 목록에는 그 자체로 다른 뜻이
     * 되기 어려운 긴 표현만 넣는다. 기본값이 비어 있는 것도 같은 이유다.
     */
    private void requireAllowedContent(String content) {
        if (blockedWords.isEmpty()) return;
        String key = BambooNicknamePolicy.normalizeText(content);
        if (blockedWords.stream().anyMatch(key::contains)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAMBOO_CONTENT_BLOCKED",
                    "사용할 수 없는 표현이 포함되어 있습니다.");
        }
    }

    // ------------------------------------------------------------------ 신고

    /**
     * 신고를 기록하고 누적 수를 반환한다. 알림 발송은 호출자가 트랜잭션이 끝난 뒤에 한다.
     */
    public long reportAs(UUID reporterUuid, Long messageId, BambooReportReason reason) {
        return sequence.writeInOrder(seq -> {
            requireEnabled();
            BambooMessage message = messages.findById(messageId)
                    .filter(BambooMessage::isVisible)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BAMBOO_MESSAGE_NOT_FOUND",
                            "메시지를 찾을 수 없습니다."));
            if (message.getUserUuid().equals(reporterUuid)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "BAMBOO_SELF_REPORT_NOT_ALLOWED",
                        "본인이 작성한 메시지는 신고할 수 없습니다.");
            }
            if (reports.existsByMessageIdAndUserUuid(messageId, reporterUuid)) {
                throw new ApiException(HttpStatus.CONFLICT, "BAMBOO_ALREADY_REPORTED",
                        "이미 신고한 메시지입니다.");
            }
            rateLimiter.checkReport(reporterUuid);
            try {
                reports.saveAndFlush(new BambooReport(messageId, reporterUuid, reason, Instant.now(clock)));
            } catch (DataIntegrityViolationException duplicate) {
                throw new ApiException(HttpStatus.CONFLICT, "BAMBOO_ALREADY_REPORTED",
                        "이미 신고한 메시지입니다.");
            }
            message.recordReport(seq);
            messages.save(message);
            return (long) message.getReportCount();
        });
    }

    @Transactional(readOnly = true)
    public long reportCountOf(Long messageId) {
        return reports.countByMessageId(messageId);
    }

    // ------------------------------------------------------------------ 관리자

    @Transactional(readOnly = true)
    public BambooAdminPageResponse reportedMessages(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, MAX_HISTORY_SIZE));
        return toAdminPage(messages.findReported(PageRequest.of(safePage, safeSize)));
    }

    @Transactional(readOnly = true)
    public BambooAdminPageResponse recentMessages(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, MAX_HISTORY_SIZE));
        Page<BambooMessage> found = messages.findAllByOrderByIdDesc(PageRequest.of(safePage, safeSize));
        return toAdminPage(found);
    }

    /**
     * 운영자는 실제 사용자 신원을 보지 않고 익명 닉네임으로 참여자를 찾는다.
     * 닉네임을 정했지만 아직 글을 쓰지 않은 참여자도 검색 결과에 포함된다.
     */
    @Transactional(readOnly = true)
    public BambooParticipantPageResponse participants(String query, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, MAX_HISTORY_SIZE));
        String keyword = query == null ? "" : query.strip();
        Page<BambooNickname> found = keyword.isEmpty()
                ? nicknames.findAllByOrderByNicknameAsc(PageRequest.of(safePage, safeSize))
                : nicknames.findByNicknameContainingIgnoreCaseOrderByNicknameAsc(
                        keyword, PageRequest.of(safePage, safeSize));
        Instant now = Instant.now(clock);
        List<BambooParticipantResponse> items = found.getContent().stream()
                .map(item -> new BambooParticipantResponse(item.getNickname(), item.getMutedUntil(),
                        item.getMutedUntil() != null && now.isBefore(item.getMutedUntil())))
                .toList();
        return new BambooParticipantPageResponse(items, found.getNumber(), found.getSize(),
                found.getTotalElements(), found.getTotalPages());
    }

    private BambooAdminPageResponse toAdminPage(Page<BambooMessage> found) {
        Map<Long, Map<BambooReportReason, Long>> reasons = reasonsFor(found.getContent());
        List<BambooAdminMessageResponse> items = found.getContent().stream()
                .map(message -> new BambooAdminMessageResponse(message.getId(), message.getSeq(),
                        message.getAnonName(), message.getContent(), message.getPublicStatus(),
                        message.getReportCount(),
                        reasons.getOrDefault(message.getId(), Map.of()), message.getCreatedAt()))
                .toList();
        return new BambooAdminPageResponse(items, found.getNumber(), found.getSize(),
                found.getTotalElements(), found.getTotalPages());
    }

    private Map<Long, Map<BambooReportReason, Long>> reasonsFor(List<BambooMessage> found) {
        if (found.isEmpty()) return Map.of();
        List<Long> ids = found.stream().map(BambooMessage::getId).toList();
        Map<Long, Map<BambooReportReason, Long>> grouped = new java.util.HashMap<>();
        for (BambooReport report : reports.findByMessageIdIn(ids)) {
            grouped.computeIfAbsent(report.getMessageId(), ignored -> new EnumMap<>(BambooReportReason.class))
                    .merge(report.getReason(), 1L, Long::sum);
        }
        return grouped;
    }

    /**
     * 상태를 바꾸면서 커서를 재발급해 변경이 실시간 스트림으로 전달되게 한다.
     * 메시지마다 개별 커서를 받으므로 화면에서도 순서대로 반영된다.
     */
    public int changeStatus(List<Long> messageIds, BambooMessageStatus status, UUID actorUuid) {
        // 예전 관리자 클라이언트의 HIDDEN 요청도 원문을 가리는 관리자 차단으로 유지한다.
        BambooMessageStatus target = status == BambooMessageStatus.HIDDEN ? BambooMessageStatus.BLOCKED : status;
        if (messageIds == null || messageIds.isEmpty()) return 0;
        Instant now = Instant.now(clock);
        int changed = 0;
        for (Long messageId : messageIds.stream().distinct().toList()) {
            Boolean applied = sequence.writeInOrder(seq -> {
                BambooMessage message = messages.findById(messageId).orElse(null);
                if (message == null || message.getStatus() == target) return Boolean.FALSE;
                message.changeStatus(target, seq, actorUuid, now);
                messages.save(message);
                return Boolean.TRUE;
            });
            if (Boolean.TRUE.equals(applied)) changed++;
        }
        return changed;
    }

    /**
     * 메시지를 지목해 그 작성자의 작성을 일정 시간 막는다.
     * 지목 대상이 메시지이므로 STAFF 는 작성자 신원을 몰라도 조치할 수 있다.
     */
    public BambooMuteResponse muteAuthorOf(Long messageId, int minutes, UUID actorUuid,
                                            String actorName, FestivalRole actorRole, String reason) {
        validateMuteDuration(minutes);
        String auditReason = validateModerationActorAndReason(actorUuid, actorName, actorRole, reason);
        return sequence.writeBatchInOrder(unused -> {
            BambooNickname nickname = authorNicknameOf(messageId);
            Instant now = Instant.now(clock);
            Instant until = minutes <= 0 ? null : now.plus(Duration.ofMinutes(minutes));
            nickname.mute(until);
            nicknames.saveAndFlush(nickname);
            saveModerationAudit(nickname, actorUuid, actorName, actorRole, minutes,
                    auditReason, messageId, now);
            return new BambooMuteResponse(until);
        });
    }

    /** 익명 닉네임을 직접 지정해 차단하거나, 0분으로 차단을 해제한다. */
    public BambooMuteResponse muteParticipant(String requestedNickname, int minutes, UUID actorUuid,
                                               String actorName, FestivalRole actorRole, String reason) {
        validateMuteDuration(minutes);
        String auditReason = validateModerationActorAndReason(actorUuid, actorName, actorRole, reason);
        String key = BambooNicknamePolicy.normalize(
                requestedNickname == null ? "" : requestedNickname.strip());
        if (key.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAMBOO_NICKNAME_REQUIRED",
                    "차단할 익명 닉네임을 선택해 주세요.");
        }
        return sequence.writeBatchInOrder(unused -> {
            BambooNickname nickname = nicknames.findByNicknameKey(key).orElseThrow(() ->
                    new ApiException(HttpStatus.NOT_FOUND, "BAMBOO_NICKNAME_NOT_FOUND",
                            "해당 익명 참여자를 찾을 수 없습니다."));
            Instant now = Instant.now(clock);
            Instant until = minutes <= 0 ? null : now.plus(Duration.ofMinutes(minutes));
            nickname.mute(until);
            nicknames.saveAndFlush(nickname);
            saveModerationAudit(nickname, actorUuid, actorName, actorRole, minutes,
                    auditReason, null, now);
            return new BambooMuteResponse(until);
        });
    }

    private String validateModerationActorAndReason(UUID actorUuid, String actorName,
                                                     FestivalRole actorRole, String reason) {
        if (actorUuid == null || (actorRole != FestivalRole.ADMIN && actorRole != FestivalRole.SUPER_ADMIN)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "BAMBOO_MANAGE_FORBIDDEN",
                    "작성 차단은 ADMIN 이상만 처리할 수 있습니다.");
        }
        String cleanActorName = actorName == null ? "" : actorName.strip();
        String cleanReason = reason == null ? "" : reason.strip();
        if (cleanActorName.isEmpty() || cleanActorName.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAMBOO_ACTOR_NAME_REQUIRED",
                    "처리자 이름을 확인할 수 없습니다.");
        }
        if (cleanReason.isEmpty() || cleanReason.length() > 200) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAMBOO_MUTE_REASON_INVALID",
                    "차단 사유를 1자 이상 200자 이하로 입력해 주세요.");
        }
        return cleanReason;
    }

    private void saveModerationAudit(BambooNickname target, UUID actorUuid, String actorName,
                                     FestivalRole actorRole, int minutes, String reason,
                                     Long sourceMessageId, Instant now) {
        moderationAudits.save(new BambooModerationAudit(target.getUserUuid(), target.getNickname(),
                actorUuid, actorName.strip(), actorRole,
                minutes <= 0 ? BambooModerationAction.UNMUTE : BambooModerationAction.MUTE,
                minutes <= 0 ? null : minutes, reason, sourceMessageId, now));
    }

    @Transactional(readOnly = true)
    public BambooModerationAuditPageResponse moderationHistory(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, MAX_HISTORY_SIZE));
        Page<BambooModerationAudit> found = moderationAudits.findAllByOrderByOccurredAtDescIdDesc(
                PageRequest.of(safePage, safeSize));
        List<BambooModerationAuditResponse> items = found.getContent().stream()
                .map(item -> new BambooModerationAuditResponse(item.getId(), item.getTargetNickname(),
                        item.getActorUuid(), item.getActorName(), item.getActorRole().name(),
                        item.getAction(), item.getDurationMinutes(), item.getReason(),
                        item.getSourceMessageId(), item.getOccurredAt()))
                .toList();
        return new BambooModerationAuditPageResponse(items, found.getNumber(), found.getSize(),
                found.getTotalElements(), found.getTotalPages());
    }

    private void validateMuteDuration(int minutes) {
        if (minutes < 0 || minutes > 525_600) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAMBOO_INVALID_MUTE_DURATION",
                    "작성 차단 시간은 0분 이상 525600분 이하로 입력해 주세요.");
        }
    }

    public BambooNicknameResponse renameAuthorOf(Long messageId, String requested) {
        String nickname = requested == null ? "" : requested.strip();
        validateNickname(nickname);
        String key = BambooNicknamePolicy.normalize(nickname);
        if (key.isEmpty() || BambooNicknamePolicy.isBlocked(key)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAMBOO_NICKNAME_INVALID",
                    "사용할 수 없는 닉네임입니다.");
        }
        try {
            return sequence.writeBatchInOrder(nextSeq -> {
                BambooNickname current = authorNicknameOf(messageId);
                if (!key.equals(current.getNicknameKey()) && nicknames.existsByNicknameKey(key)) {
                    throw new ApiException(HttpStatus.CONFLICT, "BAMBOO_NICKNAME_TAKEN",
                            "이미 사용 중인 닉네임입니다.");
                }
                current.rename(nickname, key);
                nicknames.saveAndFlush(current);
                List<BambooMessage> authored = messages.findByUserUuidOrderByIdAsc(current.getUserUuid());
                for (BambooMessage message : authored) {
                    message.renameAuthor(nickname, nextSeq.getAsLong());
                }
                messages.saveAllAndFlush(authored);
                return new BambooNicknameResponse(nickname);
            });
        } catch (DataIntegrityViolationException conflict) {
            throw new ApiException(HttpStatus.CONFLICT, "BAMBOO_NICKNAME_TAKEN",
                    "이미 사용 중인 닉네임입니다. 다른 닉네임을 입력해 주세요.");
        }
    }

    @Transactional(readOnly = true)
    public UUID authorUuidOf(Long messageId) {
        return messages.findById(messageId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BAMBOO_MESSAGE_NOT_FOUND",
                        "메시지를 찾을 수 없습니다."))
                .getUserUuid();
    }

    @Transactional(readOnly = true)
    public String nicknameOf(UUID userUuid) {
        if (org.syu_likelion.Festa_2026.user.DeletedUserIdentity.matches(userUuid))
            return org.syu_likelion.Festa_2026.user.DeletedUserIdentity.NAME;
        return nicknames.findById(userUuid).map(BambooNickname::getNickname).orElse(null);
    }

    public BambooSettingsResponse updateSettings(Boolean enabled, Boolean readOnly, Instant closesAt,
                                                 boolean clearClosesAt) {
        return sequence.writeBatchInOrder(unused -> {
            BambooSettings current = currentSettings();
            current.update(enabled, readOnly, closesAt, clearClosesAt, Instant.now(clock));
            BambooSettings saved = settings.saveAndFlush(current);
            return new BambooSettingsResponse(saved.isEnabled(), saved.isReadOnly(), saved.getClosesAt(),
                    saved.getUpdatedAt());
        });
    }

    public BambooSettingsResponse settingsView() {
        BambooSettings current = currentSettings();
        return new BambooSettingsResponse(current.isEnabled(), current.isReadOnly(), current.getClosesAt(),
                current.getUpdatedAt());
    }

    /** 관리자 실시간 화면이 변경 여부만 가볍게 확인할 때 사용한다. */
    public long currentCursor() {
        return sequence.current();
    }

    private BambooNickname authorNicknameOf(Long messageId) {
        UUID authorUuid = authorUuidOf(messageId);
        return nicknames.findById(authorUuid).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "BAMBOO_NICKNAME_NOT_FOUND",
                        "작성자의 닉네임 정보를 찾을 수 없습니다."));
    }

    /** 작성 차단은 인증 캐시를 거치지 않는다. 매 작성마다 닉네임 행을 직접 읽는다. */
    private void requireNotMuted(BambooNickname nickname, Instant now) {
        Instant mutedUntil = nickname.getMutedUntil();
        if (mutedUntil == null || !now.isBefore(mutedUntil)) return;
        long minutes = Math.max(1, Duration.between(now, mutedUntil).toMinutes() + 1);
        throw new ApiException(HttpStatus.FORBIDDEN, "BAMBOO_MUTED",
                "작성이 " + minutes + "분간 제한되었습니다.");
    }

    // ------------------------------------------------------------------ 공통

    private BambooMessageResponse toResponse(BambooMessage message, UUID viewerUuid) {
        return new BambooMessageResponse(message.getId(), message.getSeq(), message.getAnonName(),
                message.canExposeContent() ? message.getContent() : null, message.getPublicStatus(),
                message.getCreatedAt(), viewerUuid != null && viewerUuid.equals(message.getUserUuid()));
    }

    private void requireEnabled() {
        requireEnabled(currentSettings());
    }

    private void requireEnabled(BambooSettings current) {
        if (!current.isEnabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "BAMBOO_CLOSED",
                    "대나무숲이 현재 열려 있지 않습니다.");
        }
    }

    BambooSettings currentSettings() {
        return settings.findById(BambooSettings.SINGLETON_ID).orElseGet(() -> {
            synchronized (settingsInitializationLock) {
                return settings.findById(BambooSettings.SINGLETON_ID).orElseGet(this::createDefaultSettings);
            }
        });
    }

    private BambooSettings createDefaultSettings() {
        try {
            return settings.saveAndFlush(BambooSettings.openedAt(Instant.now(clock)));
        } catch (DataIntegrityViolationException race) {
            return settings.findById(BambooSettings.SINGLETON_ID).orElseThrow(() ->
                    new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                            "대나무숲 설정을 읽지 못했습니다."));
        }
    }
}
