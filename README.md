# Festa_2026 Backend

SYU 2026 축제 홈페이지 백엔드입니다. 사용자 인증과 개인정보 원본은 기존 SSO에 위임하고, 이 서비스는 `userUuid` 연결과 축제 전용 역할만 저장합니다.

## 인증 구조

- React는 축제 백엔드의 `/api/**`만 호출합니다.
- 로그인 성공 시 Access Token은 JSON 응답으로 반환됩니다. 프런트에서는 메모리에만 보관하고 `localStorage`에 저장하지 않습니다.
- SSO Refresh Token은 축제 백엔드가 `festivalRefreshToken` HttpOnly/Secure/SameSite 쿠키로 중계합니다. 서버 세션과 토큰 DB는 없습니다.
- 보호 API는 `Authorization: Bearer <accessToken>`을 사용합니다.
- 보호 API에서 Access Token 만료가 확인되면 백엔드가 한 번 자동 갱신하고 재시도합니다. 이때 새 Access Token은 `X-Access-Token` 헤더로 반환됩니다.
- React 요청에는 Refresh Token 쿠키 전달을 위해 `credentials: 'include'`를 사용해야 합니다.

개인정보(이메일, 이름, 전화번호, 학적 정보), 비밀번호와 토큰은 축제 DB에 저장하지 않습니다. `name`, `phone`, `studentNo`, `department`, `grade`, `enrollment`, `birthDate`는 SSO 응답에서 모두 `null`일 수 있습니다. 이름과 전화번호는 기본 프로필 API로 수정할 수 있으며, 학적 정보의 학생 인증 연동은 이번 범위에 포함하지 않습니다.

회원가입에서 `loginId`, `password`, `email`은 필수이며 다음 SSO 프로필 필드는 모두 선택입니다.

```json
{
  "loginId": "festival01",
  "password": "password123",
  "email": "student@example.com",
  "name": null,
  "phone": null,
  "studentNo": null,
  "department": null,
  "grade": null,
  "enrollment": null,
  "birthDate": null
}
```

`userUuid`, `createdAt`, `updatedAt`은 클라이언트가 입력하지 않고 SSO와 서버가 생성합니다.

## 환경변수

필수값:

```properties
SSO_CLIENT_ID=likelion-syu-festival
SSO_CLIENT_SECRET=issued-secret
DB_USERNAME=...
DB_PASSWORD=...
```

주요 선택값과 기본값은 [env.properties.example](src/main/resources/env.properties.example)를 참고하세요. 실제 `env.properties`는 Git에서 제외됩니다.

SSO 측 OAuth Client는 active 상태로 등록하고 다음 권한을 모두 허용해야 합니다.

- Grant types: `authorization_code`, `refresh_token`, `client_credentials`
- Scopes: `openid`, `email`, `profile`, `user.email.read`, `user.profile.read`

`authorization_code`는 사용자 로그인, `refresh_token`은 사용자 Access Token 갱신,
`client_credentials`는 QR 스캔 등 서버 간 SSO 사용자 조회에 사용합니다.
Client Secret은 백엔드 배포 환경에만 둡니다.

## API

- `POST /api/auth/signup/email/send`, `POST /api/auth/signup/email/verify`
- `POST /api/auth/signup`, `POST /api/auth/login`
- `POST /api/auth/token/refresh`, `POST /api/auth/logout`
- `GET /api/users/me`, `PATCH /api/users/me/profile`
- `POST /api/users/me/email/verification`
- `POST /api/users/me/email/verification/confirm`
- `PATCH /api/users/me/email`, `PATCH /api/users/me/password`
- `DELETE /api/users/me`
- `POST /api/qr/tokens` — 내 동적 QR 토큰 발급
- `POST /api/qr/scan` — 관리자 권한별 QR 사용자 조회

## 동적 사용자 QR

QR 문자열에는 사용자 정보나 Access Token을 넣지 않습니다. 백엔드는 256비트 난수 토큰과 만료 시각만 반환하며, 프런트엔드가 QR 이미지로 렌더링합니다.

```text
QR token --SHA-256 key--> festival_qr_tokens userUuid mapping (기본 유효시간 60초)
userUuid --> SSO client_credentials --> /api/internal/users/profiles/batch
```

`festival_qr_tokens`에는 QR 원문이나 개인정보를 저장하지 않고 토큰 해시, userUuid, 만료 시각만 저장합니다. 새 QR 발급 시 기존 QR을 삭제하지 않으며 각 토큰은 자신의 만료 시각까지 사용할 수 있습니다. 스캔 응답 범위는 축제 DB의 역할로 제한됩니다.

- `BOOTH_MANAGER`: 마스킹된 이름과 학번, 학과, 학년
- `STAFF`: 이름, 학번, 학과, 학년
- `ADMIN`: STAFF 정보와 전화번호, 이메일
- `SUPER_ADMIN`: SSO 전체 프로필과 축제 역할
- `USER`: 스캔 불가

SSO Client의 세 grant 중 서버 간 프로필 조회에는 `client_credentials`와
`user.email.read user.profile.read` scope를 사용합니다. 서비스 Access Token은 서버 메모리에 만료 시각과 함께 캐싱됩니다.

## Swagger / OpenAPI

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- OpenAPI YAML: `http://localhost:8080/v3/api-docs.yaml`

Swagger UI의 **Authorize** 버튼에 SSO Access Token을 입력하면 보호 API를 호출할 수 있습니다. `Bearer ` 접두사는 Swagger UI가 자동으로 추가합니다.

## 테스트

```powershell
.\gradlew.bat test
```

통합 테스트는 로컬 가짜 SSO HTTP 서버와 H2를 사용하며 로그인 오류, nullable 프로필, 토큰 자동 갱신과 rotation, 로그아웃, 이메일 변경, 비밀번호 변경, timeout 및 축제 역할 분리를 검증합니다.
