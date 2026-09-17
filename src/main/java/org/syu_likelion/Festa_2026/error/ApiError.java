package org.syu_likelion.Festa_2026.error;

import java.time.Instant;

public record ApiError(
        @io.swagger.v3.oas.annotations.media.Schema(description = "UI 분기 기준. message 문자열로 분기하지 않습니다.", example = "INVALID_REQUEST") String code,
        @io.swagger.v3.oas.annotations.media.Schema(description = "사용자 안내 문구. 필드별 오류 객체가 아닌 문자열입니다.") String message,
        @io.swagger.v3.oas.annotations.media.Schema(description = "오류 발생 시각, UTC ISO-8601") Instant timestamp,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        @io.swagger.v3.oas.annotations.media.Schema(description = "SSO 이메일 발송 제한에서 남은 시간이 제공될 때만 포함. 이 초만큼 재전송 버튼을 비활성화합니다. 모든 429에 존재하는 값은 아닙니다.")
        Long retryAfterSeconds) {
    public ApiError(String code, String message, Instant timestamp) {
        this(code, message, timestamp, null);
    }
    public static ApiError of(String code, String message) {
        return new ApiError(code, message, Instant.now());
    }
}
