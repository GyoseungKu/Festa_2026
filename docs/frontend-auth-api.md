# 인증·회원가입 API

[문서 목차](README.md) · [프런트 연동 시작하기](frontend-getting-started.md)

**이 문서의 순서**

- [전체 흐름](#전체-흐름)
- [로그인](#로그인)
- [명시적 토큰 갱신](#명시적-토큰-갱신)
- [로그아웃](#로그아웃)
- [학적정보 입력 방식](#학적정보-입력-방식)
- [회원가입 이메일 인증](#회원가입-이메일-인증)
- [회원가입](#회원가입)
- [주요 오류](#주요-오류)

공통 헤더와 오류 처리는 [공통 API 규약](frontend-api-common.md)을 먼저 확인합니다.

> 학교 SSO를 이용한 회원가입 학적정보 자동입력과 callback 라우트 구현은 [프런트엔드 학교 SSO 연동 가이드](frontend-school-sso.md)를 기준으로 합니다. 이 문서의 학교 SSO 부분은 API 요약입니다.

## 전체 흐름

```text
회원가입: 학적정보 입력 방식 선택 -> 이메일 인증번호 발송 -> 인증번호 확인 -> 회원가입
로그인: 아이디/비밀번호 -> Access Token 응답 + Refresh HttpOnly 쿠키
일반 요청: Bearer Access Token + credentials include
로그아웃: 서버 호출 -> 메모리 토큰/캐시 제거
```

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

### 탈퇴 계정 복구와 재가입

로그인 요청에 선택적 boolean `reactivate`를 전달할 수 있습니다. 생략하면 false이며, 처음부터 true로 전송하거나 오류 후 자동으로 true를 재요청하지 않습니다.

1. 평소처럼 로그인합니다(`reactivate` 생략 또는 false).
2. `409 ACCOUNT_REACTIVATION_REQUIRED`일 때만 “계정을 복구하시겠습니까? 보관 중인 계정과 프로필 정보가 복원됩니다.” 동의창을 표시합니다.
3. 취소하면 추가 요청 없이 종료합니다. 사용자가 명시적으로 동의한 경우에만 같은 로그인 정보와 `reactivate: true`로 재요청합니다.
4. 성공하면 기존 Access Token·Refresh 쿠키 처리와 내 정보 조회를 실행합니다. 비밀번호는 로그·URL·영구 저장소에 저장하지 않고 흐름 종료 시 화면 메모리에서도 제거합니다.

```json
{ "loginId": "festival01", "password": "Password123!", "reactivate": true }
```

| HTTP / code | 프런트 처리 |
|---|---|
| `200` | 기존 로그인 완료 처리 |
| `409 ACCOUNT_REACTIVATION_REQUIRED` | 복구 동의창 표시 |
| `409 ACCOUNT_REACTIVATION_CONFLICT` | 정보 충돌 안내·관리자 문의 |
| `401 LOGIN_FAILED` | 로그인 실패 안내, 복구 자동 재시도 금지 |
| `409 RECENTLY_WITHDRAWN_ACCOUNT` | 가입 화면에서 기존 계정 로그인·복구 또는 탈퇴 후 30일 경과 안내 |

Festa는 위 코드를 보존하고 공통 오류의 `timestamp`를 추가합니다. 문구 대신 `code`로 분기합니다. 기존 SSO의 일반 401·409는 기존 `UNAUTHORIZED`·`ACCOUNT_CONFLICT` 매핑을 유지하므로 함께 처리합니다.

SSO 정책상 탈퇴 후 30일 미만은 기존 로그인 API로 복구하며 UUID를 유지합니다. 30일 이상은 일반 가입으로 새 UUID를 발급합니다. 관리자에 의해 탈퇴된 계정은 자가 복구할 수 없으며, 복구 가능 여부와 기간 경계는 SSO가 판단합니다.

**사용자 프런트는 기존 Festa `POST /api/auth/signup`을 그대로 호출합니다.** Festa가 SSO의 `/api/auth/register`로 중계합니다. `/rejoin` API를 만들거나 프런트에서 SSO Basic 인증 정보를 전달하지 않습니다. `clientSecret`은 서버에서 관리합니다.

탈퇴 후 30일 미만에는 아이디·이메일·전화번호·학번 중 하나라도 예약 정보와 겹치면 SSO가 신규 가입을 거부하며, 각 중복 확인은 `available: false`를 반환합니다. 30일 이상이면 다른 계정이 사용하지 않는 정보로 일반 가입할 수 있습니다.

Festa는 SSO의 UUID로만 사용자를 연결합니다. 같은 UUID로 복구하고 축제 사용자 행이 남아 있으면 기존 축제 정보·권한을 사용합니다. 새 UUID는 기존 이메일·학번과 같더라도 과거 사용자 데이터나 권한을 승계하지 않습니다. 축제 서비스 이용 정보 삭제로 이미 삭제·익명화한 데이터는 SSO 복구로 복원되지 않습니다.

사용자 React 화면의 동의창은 프런트에서 구현해야 합니다. 현재 서버 렌더링 관리자 로그인 폼은 복구 동의를 받지 않으며 `reactivate=false`로 요청합니다. 관리자 페이지의 계정 복구 UI를 추가한 변경은 아닙니다.

기존 SSO 사용자가 Festa에 최초 로그인한 경우에도 같은 HTML 환영 메일이 한 번 발송됩니다. 이미 발송된 사용자에게는 이후 로그인 시 다시 발송하지 않으며 프런트에서 별도의 메일 API를 호출할 필요가 없습니다.

## 명시적 토큰 갱신

일반 보호 API는 서버가 자동 갱신을 시도하므로 보통 직접 호출할 필요가 없습니다. 앱 시작 시 메모리 Access Token이 없고 Refresh 쿠키만 남아 있을 때 사용할 수 있습니다.

본문과 Bearer 헤더는 필요하지 않으며 `credentials: "include"`로 API 로그인에서 발급한 쿠키를 전송합니다. `/admin/login`의 Swagger 열람용 쿠키는 이 API에 사용할 수 없습니다. Swagger 테스트 순서는 [별도 가이드](frontend-swagger-guide.md)를 확인합니다. 최종 401이면 메모리 토큰·사용자 캐시를 정리하고 반복 갱신하지 않습니다.

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

## 학적정보 입력 방식

회원가입의 `academicInfoSource`는 다음 두 값 중 하나입니다. 기존 클라이언트가 필드를 생략하면 `MANUAL`로 처리됩니다.

| 값 | 동작 |
|---|---|
| `MANUAL` | 이름, 학번, 학과를 사용자가 직접 입력 |
| `SCHOOL_SSO` | 학교 SSO에서 검증한 이름, 학번, 학과를 서버가 사용 |

### 학교 SSO 자동입력 흐름

상세 UI 상태, TypeScript 예시, callback 결과별 처리와 세션 주의사항은 [프런트엔드 학교 SSO 연동 가이드](frontend-school-sso.md)를 참고합니다.

```text
1. 브라우저를 GET /api/auth/school/authorize 로 이동
2. 학교 로그인 및 개인정보 제공 동의
3. 학교가 /auth/sso/callback 으로 code + state 전달
4. Festa 서버가 Code 교환, JWKS kid 선택, RS256 및 Claim 검증
5. 성공 시 설정된 화면으로 ?schoolSso=success 리다이렉트
6. GET /api/auth/school/profile 로 검증된 학적정보 조회
7. academicInfoSource=SCHOOL_SSO 로 회원가입 요청
```

`/api/auth/school/authorize`는 AJAX 호출이 아니라 브라우저 페이지 이동으로 호출합니다. `state`와 학적정보는 동일 브라우저의 서버 세션에 연결되므로 모든 요청에 쿠키가 유지되어야 합니다.

성공 후 학적정보 조회:

```http
GET /api/auth/school/profile
```

```json
{
  "studentNo": "20260001",
  "department": "컴퓨터공학과",
  "name": "홍길동",
  "consentTarget": "2026학년도 총학생회",
  "expiresAt": "2026-08-27T07:15:00Z"
}
```

학교 인증정보가 없거나 만료된 경우 `400 SCHOOL_SSO_VERIFICATION_REQUIRED`입니다. 사용자가 학교 방식을 취소하면 다음 API로 임시 학적정보를 지울 수 있습니다.

```http
DELETE /api/auth/school/profile
```

콜백 결과 쿼리값:

| 값 | 의미 |
|---|---|
| `success` | 서명 및 학적정보 검증 완료 |
| `access_denied` | 사용자가 정보 제공을 거부 |
| `invalid_state` | 세션 불일치 또는 만료된 요청 |
| `failed` | Code 교환 또는 JWT 검증 실패 |

> `SCHOOL_SSO` 가입에서는 프런트가 보낸 `name`, `studentNo`, `department`를 신뢰하지 않습니다. 서버가 세션에 보관한 검증값으로 강제 교체하며, 가입 성공 후 임시 학적정보를 즉시 폐기합니다.

DTO 검증은 교체보다 먼저 실행되므로 이 세 필드를 생략하거나 빈 값으로 보내지 않습니다. 임시 학교 프로필에서 받은 값을 포함합니다.

## 회원가입 이메일 인증

발송 제한과 남은 시간에 따른 버튼 처리는 [이메일 재전송 제한](frontend-email-cooldown.md)을 따릅니다.

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

### 중복 확인

회원가입 전에 Festa 백엔드를 통해 동아리 SSO의 중복 여부를 확인합니다. 네 API 모두 `{ "available": true }`이면 사용할 수 있는 값입니다.

```http
GET /api/auth/check/login-id?loginId=festival01
GET /api/auth/check/email?email=student%40example.com
GET /api/auth/check/student-no?studentNo=20260001
GET /api/auth/check/phone?phone=01012345678
```

```json
{ "available": true }
```

전화번호를 입력하지 않는 가입에서는 전화번호 중복 확인을 호출하지 않습니다. 중복 확인 이후 다른 사용자가 먼저 가입할 수 있으므로 최종 가입 요청의 `409 ACCOUNT_CONFLICT`도 처리해야 합니다.

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
  "birthDate": null,
  "academicInfoSource": "MANUAL"
}
```

| 필드 | 규칙 |
|---|---|
| `loginId` | 필수, 유니코드 코드 포인트 기준 4–50자. 아이디 중복 확인 API에도 동일하게 적용 |
| `password` | 필수, 8–20자, 영문 대문자·소문자·숫자·특수문자 각각 1개 이상 |
| `email` | 필수, 이메일 형식 |
| `name` | 필수, 최대 100자 |
| `phone` | 선택, `null`/빈 문자열 또는 숫자 10–11자리 |
| `studentNo` | 필수, 최대 50자 |
| `department` | 필수, 최대 100자 |
| `grade` | 선택 정수 |
| `enrollment` | 선택, 최대 50자 |
| `birthDate` | 선택, `YYYY-MM-DD` |
| `academicInfoSource` | `MANUAL` 또는 `SCHOOL_SSO`, 생략 시 `MANUAL` |

아이디는 null·빈 문자열·공백만 있는 문자열을 거부하며, 영문·숫자 외 한글·특수문자·이모지도 허용합니다. 앞뒤 공백 제거, 소문자 변환, 유니코드 정규화 없이 입력값을 그대로 SSO에 전달합니다. `user123`, `홍길동계정`, `A@한는`, `" user "`는 형식 검사를 통과합니다. 기존 사용자와 탈퇴 후 보존 중인 아이디의 중복 판정 및 최종 저장은 SSO에서 수행합니다. 중복 확인 성공은 아이디 예약이 아니므로 최종 가입의 중복 오류도 처리합니다.

프런트 길이 검사는 JavaScript의 `value.length` 대신 `Array.from(value).length`로 코드 포인트를 셉니다. HTML `maxlength="50"`만 적용하면 이모지 입력이 너무 일찍 제한될 수 있습니다. 여러 코드 포인트로 구성된 결합 문자·이모지는 화면에서 한 글자로 보여도 여러 자로 계산됩니다. 이 규칙은 회원가입과 아이디 중복 확인에 적용하며 로그인·계정 복구의 기존 검증은 유지합니다.

회원가입 비밀번호는 공백을 제외한 ASCII 출력 문자(U+0021–U+007E)만 허용합니다. 특수문자는 해당 범위의 영문·숫자 외 기호(`!`, `@`, `#`, `_` 등)입니다. 공백·탭·줄바꿈·한글·이모지는 허용하지 않으며 입력값을 trim하지 않습니다. `Abcdef1!`는 허용되고 `abcdef1!`는 거부됩니다.

누락 또는 공백뿐인 값의 검증 메시지는 `password is required`, 나머지 정책 위반은 `password must be 8-20 characters and include uppercase, lowercase, digit, and special character (ASCII only, no spaces)`입니다. API 오류 메시지에는 필드명 접두사 `password: `가 붙습니다. 이 정책은 회원가입과 [비밀번호 재설정](frontend-account-recovery-api.md)에 적용하며, 재설정의 필드명 접두사는 `newPassword: `입니다. 로그인·로그인 후 비밀번호 변경은 기존 정책을 사용합니다.

성공 `200`:

```json
{ "userUuid": "123e4567-e89b-12d3-a456-426614174000" }
```

가입 성공 후 Festa가 별도의 HTML 가입 환영 메일을 비동기로 발송합니다. 메일 발송 장애는 회원가입 API의 성공 응답에 영향을 주지 않습니다.

## 주요 오류

| HTTP | code | 의미 |
|---|---|---|
| `400` | `INVALID_REQUEST`, `SSO_INVALID_REQUEST` | 입력 또는 인증번호 오류 |
| `401` | `UNAUTHORIZED` | 로그인 정보 오류 또는 인증 만료 |
| `403` | `ACCOUNT_FORBIDDEN` | 사용할 수 없는 계정 |
| `409` | `ACCOUNT_CONFLICT` | 아이디 또는 이메일 중복 |
| `429` | `TOO_MANY_REQUESTS` | 요청 횟수 초과 |
| `429` | `EMAIL_SEND_COOLDOWN` | `retryAfterSeconds` 동안 재전송 비활성화 |
| `429` | `RATE_LIMIT_EXCEEDED` | 이메일 또는 IP의 시간당 발송 한도 초과 |
| `502` | `SSO_BAD_GATEWAY` | SSO 응답 처리 실패 |
| `503` | `SSO_UNAVAILABLE` | SSO 연결 장애 |
