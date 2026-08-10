package org.syu_likelion.Festa_2026.monitoring;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/presence")
@Tag(name = "Frontend Presence", description = "개인정보 없이 현재 React 접속 규모를 추정")
public class PresenceController {
    private final PresenceTracker tracker;

    public PresenceController(PresenceTracker tracker) {
        this.tracker = tracker;
    }

    @PostMapping("/heartbeat")
    @Operation(summary = "프런트 접속 heartbeat",
            description = "로그인 여부와 무관하게 익명 세션과 현재 route를 메모리에 갱신합니다. 저장 데이터는 TTL 뒤 자동 삭제됩니다.")
    ResponseEntity<Void> heartbeat(@Valid @RequestBody HeartbeatRequest body) {
        tracker.heartbeat(body.sessionId(), body.route());
        return ResponseEntity.noContent().build();
    }

    public record HeartbeatRequest(
            @NotNull UUID sessionId,
            @NotBlank @Size(max = 200)
            @Pattern(regexp = "^/(?!/)[^?#\\p{Cntrl}]*$", message = "쿼리 문자열이나 fragment가 없는 route만 허용됩니다")
            String route) { }
}
