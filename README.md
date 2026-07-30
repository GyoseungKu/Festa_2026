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

SSO 측에는 별도로 OAuth Client를 등록해야 합니다: `authorization_code,refresh_token`, `openid,email,profile`, active 상태를 권장합니다. Client Secret은 백엔드 배포 환경에만 둡니다.

## API

- `POST /api/auth/signup/email/send`, `POST /api/auth/signup/email/verify`
- `POST /api/auth/signup`, `POST /api/auth/login`
- `POST /api/auth/token/refresh`, `POST /api/auth/logout`
- `GET /api/users/me`, `PATCH /api/users/me/profile`
- `POST /api/users/me/email/verification`
- `POST /api/users/me/email/verification/confirm`
- `PATCH /api/users/me/email`, `PATCH /api/users/me/password`
- `DELETE /api/users/me`

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
