# 로그인 전 계정 복구 API 연동 가이드

[문서 목차](README.md) · [프런트 연동 시작하기](frontend-getting-started.md)

아이디 찾기와 비밀번호 재설정은 로그인 전 공개 API입니다. React가 SSO를 직접 호출하지 않고 반드시 Festa 백엔드만 호출해야 하며, `SSO_CLIENT_SECRET`은 프런트에 두지 않습니다.

공통 오류 응답과 fetch 처리는 [공통 API 규약](frontend-api-common.md)을 따릅니다.

## 아이디 찾기

### 1. 인증번호 발송

```http
POST /api/auth/email/send
Content-Type: application/json
```

```json
{
  "email": "student@example.com",
  "purpose": "FIND_ID"
}
```

성공 응답은 계정 존재 여부와 관계없이 동일합니다.

```json
{
  "message": "입력한 정보와 일치하는 계정이 있다면 인증번호를 발송했습니다."
}
```

### 2. 인증번호 확인 및 아이디 반환

```http
POST /api/auth/email/find-id/verify
Content-Type: application/json
```

```json
{
  "email": "student@example.com",
  "code": "123456"
}
```

```json
{
  "loginId": "festival01"
}
```

SSO 계약상 성공 시 로그인 아이디 원문이 반환되므로 프런트는 필요한 화면에서만 표시하고 브라우저 저장소에 보관하지 않습니다.

## 비밀번호 재설정

### 1. 인증번호 발송

```http
POST /api/auth/email/send
Content-Type: application/json
```

```json
{
  "loginId": "festival01",
  "email": "student@example.com",
  "purpose": "RESET_PASSWORD"
}
```

발송 응답은 아이디·이메일 일치 여부와 관계없이 아이디 찾기와 같은 일반 문구를 반환합니다.

### 2. 인증번호 확인과 새 비밀번호 적용

SSO는 별도 `resetToken`을 발급하지 않습니다. 인증번호와 새 비밀번호를 한 요청으로 보내며 성공하면 즉시 변경됩니다.

```http
POST /api/auth/email/reset-password/verify
Content-Type: application/json
```

```json
{
  "loginId": "festival01",
  "email": "student@example.com",
  "code": "123456",
  "newPassword": "NewPassword123!"
}
```

```json
{
  "message": "비밀번호가 재설정되었습니다. 새 비밀번호로 로그인해 주세요."
}
```

`newPassword`는 회원가입과 동일하게 8–20자이며 영문 대문자·소문자·숫자·특수문자를 각각 1개 이상 포함해야 합니다. 공백을 제외한 ASCII 출력 문자(`U+0021`부터 `U+007E`)만 허용하므로 공백·탭·줄바꿈·한글·이모지는 거부합니다. 입력값을 trim하지 않습니다. 검증 실패는 `400 INVALID_REQUEST`이며 오류 메시지의 필드명은 `newPassword`입니다. 성공 응답은 현재 브라우저의 Festa Refresh Token 쿠키를 제거하므로 `credentials: "include"`로 호출합니다.

## 프런트 화면 흐름

- 아이디 찾기와 비밀번호 재설정의 이메일 발송 화면은 성공 응답으로 계정 존재 여부를 추측하지 않습니다.
- 발송 성공 후 이메일과 목적을 화면 상태에 유지하고 인증번호 입력 단계로 이동합니다.
- 인증번호는 React 상태에만 두고 URL, `localStorage`, analytics에 넣지 않습니다.
- 재설정 성공 후 메모리 Access Token과 사용자 캐시를 비우고 로그인 화면으로 replace 이동합니다.
- 제출 버튼은 요청 중 비활성화하여 같은 인증번호를 중복 전송하지 않습니다.

## 오류 처리

| HTTP | 대표 code | 권장 화면 문구 |
|---|---|---|
| `400` | `INVALID_REQUEST`, `LOGIN_ID_REQUIRED` | 입력 정보를 확인해 주세요. |
| `400` | `ACCOUNT_RECOVERY_FAILED`, `SSO_INVALID_REQUEST` | 입력 정보 또는 인증번호를 확인해 주세요. |
| `429` | `RECOVERY_ATTEMPTS_EXCEEDED`, `TOO_MANY_REQUESTS` | 잠시 후 다시 시도해 주세요. |
| `429` | `EMAIL_SEND_COOLDOWN` | 인증코드 발송 대기, `retryAfterSeconds`만큼 대기 |
| `429` | `RATE_LIMIT_EXCEEDED` | 이메일 또는 IP의 시간당 발송 한도 초과 |

아이디 찾기·비밀번호 재설정의 발송 제한과 재전송 버튼 처리는 [이메일 재전송 제한](frontend-email-cooldown.md)을 확인합니다.
| `503` | `SSO_UNAVAILABLE` | 인증 서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요. |
| `502` | `SSO_BAD_GATEWAY` | 일시적인 오류가 발생했습니다. |

인증번호와 새 비밀번호를 로그, analytics metadata, URL query string, `localStorage`에 기록하지 않습니다.

## 검증 범위와 제한

- 복구 인증번호는 숫자 6자리입니다. 회원가입·이메일 변경의 코드 DTO(4–12자)와 구분합니다. 복구의 `loginId`는 최대 100자이며 회원가입의 4–50자 제한을 그대로 적용하지 않습니다.
- 인증번호의 만료·일회성 처리와 다른 기기 토큰 폐기는 외부 SSO 책임입니다. 해당 구현은 이 저장소에 없으므로 재사용 가능 여부나 전체 세션 폐기 여부를 현재 Festa 코드만으로 단정할 수 없습니다.
- Festa는 비밀번호 재설정 검증을 이메일 해시 기준 10분당 5회로 추가 제한하지만, 단일 애플리케이션 인스턴스의 메모리 제한이므로 SSO 자체 보완을 대체하지 않습니다.
- 발송 성공과 확인 성공은 모두 200이며 토큰을 발급하지 않습니다. 재설정 후 새 비밀번호로 다시 로그인합니다.
