package org.syu_likelion.Festa_2026.sso;

public final class SsoEmailRateLimitException extends SsoException {
    private final String code;
    private final Long retryAfterSeconds;

    public SsoEmailRateLimitException(String code, Long retryAfterSeconds) {
        super(429, "EMAIL_SEND_COOLDOWN".equals(code)
                ? "인증코드 재전송까지 " + retryAfterSeconds + "초 기다려 주세요."
                : "이메일 인증 요청 한도를 초과했습니다. 잠시 후 다시 시도해 주세요.");
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String code() { return code; }
    public Long retryAfterSeconds() { return retryAfterSeconds; }
}
