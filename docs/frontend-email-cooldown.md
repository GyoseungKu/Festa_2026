# 이메일 인증코드 재전송 제한

[문서 목차](README.md) · [공통 API 규약](frontend-api-common.md)

회원가입·아이디 찾기·비밀번호 재설정·이메일 변경의 인증코드 발송에 적용합니다. SSO에서 제한을 판정하고 Festa는 오류 코드와 남은 대기 시간을 중계합니다.

## SSO의 발송 제한

- 동일 이메일 기준 60초에 1회입니다. 이메일의 대소문자와 앞뒤 공백을 정리해 동일 주소로 판단합니다.
- 차단된 요청을 반복해도 대기 시간은 연장되지 않습니다.
- 이메일별 시간당 5회·IP별 시간당 15회 제한도 유지됩니다. 60초 후에도 시간당 제한에 걸리면 `RATE_LIMIT_EXCEEDED`가 반환됩니다.
- 제한 기록은 SSO 서버 메모리에 있어 SSO 인스턴스별로 적용됩니다. Festa에 별도 재전송 타이머나 발송 횟수 저장소를 추가하지 않습니다.

위 정책은 제공된 SSO 변경 계약입니다. Festa 통합 테스트는 SSO 오류 중계 동작을 검증하며 SSO 내부의 시간 경계·동시 요청 처리를 검증하지 않습니다. Festa의 이메일 DTO는 `@Email`을 먼저 검사하므로 프런트는 입력 앞뒤 공백을 정리해 전송합니다. IP 한도는 SSO가 식별한 IP 기준이며 이 문서는 프록시 환경에서 브라우저 원본 IP가 전달된다고 보장하지 않습니다.

## Festa 오류 응답

18초 후 재요청하여 42초가 남았다면 HTTP 429와 `Retry-After: 42`를 반환합니다.

```json
{
  "code": "EMAIL_SEND_COOLDOWN",
  "message": "인증코드 재전송까지 42초 기다려 주세요.",
  "timestamp": "2026-09-15T02:00:18Z",
  "retryAfterSeconds": 42
}
```

`timestamp`는 Festa의 공통 오류 필드입니다. 남은 초는 SSO JSON의 유효한 `retryAfterSeconds`를 사용하고, 없으면 숫자형 `Retry-After` 헤더에서 읽습니다. 두 응답 위치에 같은 값을 제공합니다. 잘못된 SSO 본문이나 대기 시간을 해석할 수 없는 응답은 기존 `429 TOO_MANY_REQUESTS`로 처리합니다.

`RATE_LIMIT_EXCEEDED`도 HTTP 429로 전달합니다. SSO가 남은 시간을 제공하지 않으면 `retryAfterSeconds`와 `Retry-After`를 생략합니다. 모든 429를 60초 제한으로 가정하지 않습니다.

## 프런트 처리

[공통 fetch 래퍼](frontend-api-common.md)의 `ApiRequestError.retryAfterSeconds`를 사용합니다.

```ts
// resendDisabledUntil은 화면 상태로 보관하고 남은 초를 표시합니다.
if (error instanceof ApiRequestError && error.code === "EMAIL_SEND_COOLDOWN") {
  const seconds = error.retryAfterSeconds;
  if (seconds !== null) {
    setResendDisabledUntil(Date.now() + seconds * 1000);
  }
}
```

남은 초만큼 재전송 버튼을 비활성화합니다. 성공적으로 발송했을 때는 60초 대기를 시작하고, 거부 응답은 서버가 알려준 남은 시간으로 갱신합니다. 클릭할 때마다 임의로 60초를 다시 시작하지 않습니다. 시간 만료 후 사용자가 재요청하도록 하고 자동 발송하지 않습니다. 시간당 제한은 별도 안내를 표시합니다.

각 요청 필드와 인증 조건은 [회원가입](frontend-auth-api.md), [계정 복구](frontend-account-recovery-api.md), [이메일 변경](frontend-user-api.md)을 확인합니다. 비밀번호 재설정의 인증번호 **검증**에 적용되는 Festa의 별도 시도 제한과 인증코드 **발송** 제한은 구분합니다.
