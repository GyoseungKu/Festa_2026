package org.syu_likelion.Festa_2026.bamboo;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;

public final class BambooDtos {
    private BambooDtos() { }

    public record BambooRoomResponse(boolean enabled, boolean readOnly, Instant closesAt,
                                     String nickname, long cursor) { }

    /** 삭제·숨김 메시지는 {@code content}가 null이다. 클라이언트는 {@code status}로 추가·제거를 판단한다. */
    public record BambooMessageResponse(Long id, long seq, String anonName, String content,
                                        BambooMessageStatus status, Instant createdAt, boolean mine) { }

    /** 스트림과 과거 조회가 같은 응답을 쓴다. {@code cursor}는 이어서 스트림을 받을 시작점이다. */
    public record BambooStreamResponse(long cursor, List<BambooMessageResponse> messages) { }

    public record BambooMessageCreateRequest(@NotBlank String content) { }

    public record BambooNicknameRequest(@NotBlank String nickname) { }

    public record BambooNicknameResponse(String nickname) { }
}
