# 로그인 전 계정 복구 API 연동 가이드

아이디 찾기와 비밀번호 재설정은 로그인 전 공개 API입니다. React가 SSO를 직접 호출하지 않고 반드시 Festa 백엔드만 호출해야 하며, `SSO_CLIENT_SECRET`은 프런트에 두지 않습니다.

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

`newPassword`는 Festa 백엔드에서 8~128자로 검증합니다. 성공 응답은 현재 브라우저의 Festa Refresh Token 쿠키를 제거하므로 `credentials: "include"`로 호출합니다.

## 오류 처리

| HTTP | 의미 | 권장 화면 문구 |
|---|---|---|
| `400` | 입력 형식, 인증번호, 아이디·이메일 불일치 | 입력 정보 또는 인증번호를 확인해 주세요. |
| `429` | 발송 또는 검증 시도 초과 | 잠시 후 다시 시도해 주세요. |
| `503` | SSO 연결 지연·장애 | 인증 서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요. |
| `502` | SSO 응답 계약 오류 | 일시적인 오류가 발생했습니다. |

인증번호와 새 비밀번호를 로그, analytics metadata, URL query string, `localStorage`에 기록하지 않습니다.

## 현재 SSO 보안 제약

- SSO는 비밀번호 재설정 후 기존 Access/Refresh Token과 다른 기기 세션을 폐기하지 않습니다.
- SSO 인증번호는 만료 전 재사용될 수 있습니다.
- Festa는 비밀번호 재설정 검증을 이메일 해시 기준 10분당 5회로 추가 제한하지만, 단일 애플리케이션 인스턴스의 메모리 제한이므로 SSO 자체 보완을 대체하지 않습니다.
- 운영 전 SSO에 인증번호 일회성 처리와 재설정 후 전체 세션 폐기를 추가하는 것을 권장합니다.
