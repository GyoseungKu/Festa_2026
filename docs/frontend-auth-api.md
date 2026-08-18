# 인증·회원가입 API

공통 헤더와 오류 처리는 [공통 API 규약](frontend-api-common.md)을 먼저 확인합니다.

## 전체 흐름

```text
회원가입: 이메일 인증번호 발송 -> 인증번호 확인 -> 회원가입
로그인: 아이디/비밀번호 -> Access Token 응답 + Refresh HttpOnly 쿠키
일반 요청: Bearer Access Token + credentials include
로그아웃: 서버 호출 -> 메모리 토큰/캐시 제거
```

## 회원가입 이메일 인증

### 인증번호 발송

```http
POST /api/auth/signup/email/send
Content-Type: application/json
```

```json
{ "email": "student@example.com" }
```

성공 `200`:

```json
{ "message": "인증번호를 발송했습니다." }
```

### 인증번호 확인

```http
POST /api/auth/signup/email/verify
Content-Type: application/json
```

```json
{ "email": "student@example.com", "code": "123456" }
```

성공 `200`:

```json
{ "message": "이메일 인증이 완료되었습니다." }
```

## 회원가입

```http
POST /api/auth/signup
Content-Type: application/json
```

```json
{
  "loginId": "festival01",
  "password": "Password123!",
  "email": "student@example.com",
  "name": "홍길동",
  "phone": null,
  "studentNo": "20260001",
  "department": "컴퓨터공학과",
  "grade": null,
  "enrollment": null,
  "birthDate": null
}
```

| 필드 | 규칙 |
|---|---|
| `loginId` | 필수, 최대 100자 |
| `password` | 필수, 8~128자 |
| `email` | 필수, 이메일 형식 |
| `name` | 필수, 최대 100자 |
| `phone` | 선택, `null`/빈 문자열 또는 숫자 10~11자리 |
| `studentNo` | 필수, 최대 50자 |
| `department` | 필수, 최대 100자 |
| `grade` | 선택 정수 |
| `enrollment` | 선택, 최대 50자 |
| `birthDate` | 선택, `YYYY-MM-DD` |

성공 `200`:

```json
{ "userUuid": "123e4567-e89b-12d3-a456-426614174000" }
```

가입 성공 후 Festa가 별도의 HTML 가입 환영 메일을 비동기로 발송합니다. 메일 발송 장애는 회원가입 API의 성공 응답에 영향을 주지 않습니다.

## 로그인

```http
POST /api/auth/login
Content-Type: application/json
```

```json
{ "loginId": "festival01", "password": "Password123!" }
```

성공 `200`:

```json
{ "accessToken": "eyJ..." }
```

응답의 Refresh Token은 HttpOnly `Set-Cookie`로 저장됩니다. Access Token만 메모리 인증 store에 보관한 다음 `/api/users/me`를 조회해 사용자와 축제 권한을 초기화합니다.

기존 SSO 사용자가 Festa에 최초 로그인한 경우에도 같은 HTML 환영 메일이 한 번 발송됩니다. 이미 발송된 사용자에게는 이후 로그인 시 다시 발송하지 않으며 프런트에서 별도의 메일 API를 호출할 필요가 없습니다.

## 명시적 토큰 갱신

일반 보호 API는 서버가 자동 갱신을 시도하므로 보통 직접 호출할 필요가 없습니다. 앱 시작 시 메모리 Access Token이 없고 Refresh 쿠키만 남아 있을 때 사용할 수 있습니다.

```http
POST /api/auth/token/refresh
```

성공 `200`:

```json
{ "accessToken": "new-access-token" }
```

## 로그아웃

```http
POST /api/auth/logout
Authorization: Bearer ACCESS_TOKEN
```

성공은 `204 No Content`입니다. Access Token이 없더라도 로컬 Refresh 쿠키 삭제는 가능합니다.

프런트는 응답 성공 여부와 관계없이 사용자가 로그아웃을 선택하면 메모리 토큰과 사용자 캐시를 제거하고 로그인 화면으로 이동하는 편이 안전합니다.

## 주요 오류

| HTTP | code | 의미 |
|---|---|---|
| `400` | `INVALID_REQUEST`, `SSO_INVALID_REQUEST` | 입력 또는 인증번호 오류 |
| `401` | `UNAUTHORIZED` | 로그인 정보 오류 또는 인증 만료 |
| `403` | `ACCOUNT_FORBIDDEN` | 사용할 수 없는 계정 |
| `409` | `ACCOUNT_CONFLICT` | 아이디 또는 이메일 중복 |
| `429` | `TOO_MANY_REQUESTS` | 요청 횟수 초과 |
| `502` | `SSO_BAD_GATEWAY` | SSO 응답 처리 실패 |
| `503` | `SSO_UNAVAILABLE` | SSO 연결 장애 |
