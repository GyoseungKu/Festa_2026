package org.syu_likelion.Festa_2026.birthday;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.syu_likelion.Festa_2026.birthday.BirthdayMessageDtos.BirthdayMessagePageResponse;
import org.syu_likelion.Festa_2026.birthday.BirthdayMessageDtos.BirthdayMessageResponse;
import org.syu_likelion.Festa_2026.birthday.BirthdayMessageDtos.HeartResponse;
import org.syu_likelion.Festa_2026.birthday.BirthdayMessageDtos.MyBirthdayMessageResponse;
import org.syu_likelion.Festa_2026.birthday.BirthdayMessageDtos.PublicAuthor;
import org.syu_likelion.Festa_2026.error.ApiException;

@Service
public class BirthdayMessageService {
    private static final int MAX_CONTENT_CODE_POINTS = 100;
    private static final int MAX_PAGE_SIZE = 100;

    private final BirthdayMessageRepository messages;
    private final BirthdayMessageHeartRepository hearts;

    public BirthdayMessageService(BirthdayMessageRepository messages,
                                  BirthdayMessageHeartRepository hearts) {
        this.messages = messages;
        this.hearts = hearts;
    }

    @Transactional(readOnly = true)
    public BirthdayMessagePageResponse list(UUID viewerUuid, BirthdayMessageSort order, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        Page<BirthdayMessage> result = messages.findAll(PageRequest.of(safePage, safeSize, sort(order)));
        Set<Long> hearted = heartedMessageIds(result.getContent(), viewerUuid);
        List<BirthdayMessageResponse> items = result.getContent().stream()
                .map(message -> toResponse(message, viewerUuid, hearted.contains(message.getId()))).toList();
        return new BirthdayMessagePageResponse(items, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public BirthdayMessageResponse get(Long id, UUID viewerUuid) {
        BirthdayMessage message = find(id);
        boolean hearted = viewerUuid != null && hearts.existsByMessage_IdAndUserUuid(id, viewerUuid);
        return toResponse(message, viewerUuid, hearted);
    }

    @Transactional(readOnly = true)
    public MyBirthdayMessageResponse getMine(UUID viewerUuid) {
        return messages.findByAuthorUuid(viewerUuid)
                .map(message -> new MyBirthdayMessageResponse(true, toResponse(message, viewerUuid,
                        hearts.existsByMessage_IdAndUserUuid(message.getId(), viewerUuid))))
                .orElseGet(() -> new MyBirthdayMessageResponse(false, null));
    }

    @Transactional
    public BirthdayMessageResponse createAs(UUID authorUuid, String content, String department,
                                            String studentNo, String name) {
        String normalized = normalizeContent(content);
        BirthdayMessage message = new BirthdayMessage(authorUuid, normalized, cleanOptional(department),
                maskStudentNo(studentNo), maskName(name));
        try {
            return toResponse(messages.saveAndFlush(message), authorUuid, false);
        } catch (DataIntegrityViolationException duplicate) {
            throw new ApiException(HttpStatus.CONFLICT, "BIRTHDAY_MESSAGE_ALREADY_EXISTS",
                    "한 사람당 하나의 생일축하 쪽지만 작성할 수 있습니다.");
        }
    }

    @Transactional
    public void deleteOwnAs(Long id, UUID actorUuid) {
        BirthdayMessage message = find(id);
        if (!message.getAuthorUuid().equals(actorUuid)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "BIRTHDAY_MESSAGE_DELETE_FORBIDDEN",
                    "본인이 작성한 쪽지만 삭제할 수 있습니다.");
        }
        messages.delete(message);
        messages.flush();
    }

    @Transactional
    public void deleteAsAdmin(Long id) {
        messages.delete(find(id));
        messages.flush();
    }

    @Transactional
    public HeartResponse addHeartAs(Long id, UUID actorUuid) {
        BirthdayMessage message = findForUpdate(id);
        if (message.getAuthorUuid().equals(actorUuid)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SELF_HEART_NOT_ALLOWED",
                    "본인의 쪽지에는 하트를 누를 수 없습니다.");
        }
        if (!hearts.existsByMessage_IdAndUserUuid(id, actorUuid)) {
            hearts.saveAndFlush(new BirthdayMessageHeart(message, actorUuid));
            messages.incrementHeartCount(id);
        }
        BirthdayMessage updated = find(id);
        return new HeartResponse(id, updated.getHeartCount(), true);
    }

    @Transactional
    public HeartResponse removeHeartAs(Long id, UUID actorUuid) {
        findForUpdate(id);
        if (hearts.deleteByMessage_IdAndUserUuid(id, actorUuid) > 0) {
            hearts.flush();
            messages.decrementHeartCount(id);
        }
        BirthdayMessage updated = find(id);
        return new HeartResponse(id, updated.getHeartCount(), false);
    }

    @Transactional(readOnly = true)
    public Page<BirthdayMessage> listEntities(BirthdayMessageSort order, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        return messages.findAll(PageRequest.of(safePage, safeSize, sort(order)));
    }

    @Transactional(readOnly = true)
    public Page<BirthdayMessageHeart> listHeartEntities(Long messageId, int page, int size) {
        find(messageId);
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        return hearts.findByMessage_Id(messageId,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @Transactional(readOnly = true)
    public BirthdayMessage findEntity(Long id) { return find(id); }

    private BirthdayMessage find(Long id) {
        return messages.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                "BIRTHDAY_MESSAGE_NOT_FOUND", "생일축하 쪽지를 찾을 수 없습니다."));
    }

    private BirthdayMessage findForUpdate(Long id) {
        return messages.findForUpdateById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                "BIRTHDAY_MESSAGE_NOT_FOUND", "생일축하 쪽지를 찾을 수 없습니다."));
    }

    private Set<Long> heartedMessageIds(List<BirthdayMessage> items, UUID viewerUuid) {
        if (viewerUuid == null || items.isEmpty()) return Set.of();
        List<Long> ids = items.stream().map(BirthdayMessage::getId).toList();
        Set<Long> result = new HashSet<>();
        hearts.findByMessage_IdInAndUserUuid(ids, viewerUuid)
                .forEach(heart -> result.add(heart.getMessageId()));
        return result;
    }

    private BirthdayMessageResponse toResponse(BirthdayMessage message, UUID viewerUuid, boolean hearted) {
        return new BirthdayMessageResponse(message.getId(), message.getContent(),
                new PublicAuthor(message.getPublicDepartment(), message.getPublicMaskedStudentNo(),
                        message.getPublicMaskedName()), message.getHeartCount(), hearted,
                viewerUuid != null && viewerUuid.equals(message.getAuthorUuid()), message.getCreatedAt());
    }

    private Sort sort(BirthdayMessageSort order) {
        BirthdayMessageSort safe = order == null ? BirthdayMessageSort.LATEST : order;
        return switch (safe) {
            case LATEST -> Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
            case OLDEST -> Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"));
            case MOST_LIKED -> Sort.by(Sort.Order.desc("heartCount"),
                    Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        };
    }

    private String normalizeContent(String value) {
        String cleaned = value == null ? "" : value.trim();
        int length = cleaned.codePointCount(0, cleaned.length());
        if (cleaned.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BIRTHDAY_MESSAGE_CONTENT_REQUIRED",
                    "생일축하 메시지를 입력해 주세요.");
        }
        if (length > MAX_CONTENT_CODE_POINTS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BIRTHDAY_MESSAGE_CONTENT_TOO_LONG",
                    "생일축하 메시지는 최대 100자까지 작성할 수 있습니다.");
        }
        return cleaned;
    }

    static String maskStudentNo(String value) {
        if (value == null || value.isBlank()) return "미등록";
        String cleaned = value.trim();
        if (cleaned.length() <= 4) return "*".repeat(cleaned.length());
        return cleaned.substring(0, 4) + "*".repeat(cleaned.length() - 4);
    }

    static String maskName(String value) {
        if (value == null || value.isBlank()) return "미등록";
        StringBuilder result = new StringBuilder();
        StringBuilder token = new StringBuilder();
        value.trim().codePoints().forEach(codePoint -> {
            if (Character.isWhitespace(codePoint) || codePoint == '-') {
                appendMaskedToken(result, token);
                result.appendCodePoint(codePoint);
            } else {
                token.appendCodePoint(codePoint);
            }
        });
        appendMaskedToken(result, token);
        return result.toString();
    }

    private static void appendMaskedToken(StringBuilder result, StringBuilder token) {
        if (token.isEmpty()) return;
        int[] points = token.toString().codePoints().toArray();
        if (points.length == 1) result.append('*');
        else {
            result.appendCodePoint(points[0]);
            result.append("*".repeat(points.length - 2 < 1 ? 1 : points.length - 2));
            if (points.length >= 3) result.appendCodePoint(points[points.length - 1]);
        }
        token.setLength(0);
    }

    private String cleanOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
