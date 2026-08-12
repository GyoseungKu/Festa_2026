# Festa_2026 Backend

삼육대학교 2026 천보축전 홈페이지 백엔드입니다. 사용자 인증과 개인정보 원본은 기존 SSO에 위임하고, 이 서비스는 `userUuid` 연결, 축제 전용 역할과 축제 도메인 데이터만 관리합니다.

## 기술 구성

- Java 21, Spring Boot 4.1
- Spring MVC, Validation, Security, Thymeleaf
- Spring Data JPA, MariaDB
- AWS SDK S3를 이용한 Cloudflare R2 업로드
- Springdoc Swagger UI
- React 사용자 페이지용 REST API
- Thymeleaf 관리자 페이지

기본 실행 포트는 `8888`입니다.

## 인증 구조

```text
React 사용자 페이지
    → Authorization: Bearer {SSO Access Token}
    → Festa Backend
    → SSO

Thymeleaf 관리자 페이지
    → /admin 전용 HttpOnly Access/Refresh 쿠키
    → Festa Backend
    → SSO
```

- React는 축제 백엔드의 `/api/**`만 호출합니다.
- 로그인 성공 시 Access Token은 JSON으로 반환되며 프런트 메모리에만 보관합니다. `localStorage`에 저장하지 않습니다.
- Refresh Token은 `festivalRefreshToken` HttpOnly/Secure/SameSite 쿠키로 중계합니다.
- 보호 API는 `Authorization: Bearer <accessToken>`을 사용합니다.
- Access Token 만료 시 백엔드가 Refresh Token으로 한 번 자동 갱신하고 요청을 재시도합니다.
- 새 Access Token은 `X-Access-Token` 응답 헤더로 반환됩니다.
- React 요청은 Refresh Token 쿠키 전달을 위해 `credentials: "include"`를 사용합니다.
- 비밀번호, Access/Refresh Token과 개인정보 원본은 축제 DB에 저장하지 않습니다.
- 사용자는 `loginId`가 아니라 SSO의 `userUuid`로 연결합니다.

관리자 페이지는 React와 별도로 `/admin` 경로 전용 HttpOnly 쿠키를 사용합니다. 서버 세션 저장소는 사용하지 않습니다.

## 축제 역할

SSO의 `ssoRole`을 축제 운영 권한으로 사용하지 않습니다. 축제 역할은 `festival_roles`에서 별도로 관리합니다.

| 역할 | 용도 |
|---|---|
| `SUPER_ADMIN` | 최고 관리자 |
| `ADMIN` | 총학생회 관리자, 공연팀 관리 가능 |
| `STAFF` | 총학생회 운영진 |
| `BOOTH_MANAGER` | 부스 운영자 |
| `USER` | 일반 이용자 |

최초 로그인 사용자는 `festival_users`에 `userUuid`로 연결되고 기본 `USER` 역할을 받습니다.

## 회원가입 필수 정보와 nullable 프로필

회원가입에서 `loginId`, `email`, `password`, `name`, `department`, `studentNo`는 필수입니다. `phone`은 선택이며, 현재 회원가입 화면에서 받지 않는 `grade`, `enrollment`, `birthDate`도 nullable입니다.

```json
{
  "loginId": "festival01",
  "password": "password123",
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

회원가입 요청에서는 `name`, `studentNo`, `department`가 필수지만, 기존 SSO 계정이나 SSO 조회 응답에서는 해당 값이 여전히 `null`일 수 있습니다. 따라서 내 정보 응답 DTO와 조회 화면은 기존 방침대로 nullable을 허용합니다. `phone`, `grade`, `enrollment`, `birthDate`도 nullable이며 `userUuid`, `createdAt`, `updatedAt`은 클라이언트가 입력하지 않습니다. 학교 학생 인증 연동은 아직 구현하지 않았습니다.

## 환경변수

로컬 개발 시 [env.properties.example](src/main/resources/env.properties.example)을 복사해 `src/main/resources/env.properties`를 만들 수 있습니다. 실제 `env.properties`는 Git에서 제외되지만 classpath 리소스이므로 운영 비밀값은 가능하면 서버 환경변수나 외부 Secret으로 주입합니다.

최소 필수값:

```properties
DB_USERNAME=...
DB_PASSWORD=...
SSO_CLIENT_ID=likelion-syu-festival
SSO_CLIENT_SECRET=...
R2_ACCESS_KEY=...
R2_SECRET_KEY=...
MAIL_USERNAME=...
MAIL_PASSWORD=...
```

MySQL `caching_sha2_password` 계정을 MariaDB Connector/J로 TLS 없이 연결하면서 서버 RSA 키 파일을 별도로 배포하지 않는 경우 JDBC URL에 `allowPublicKeyRetrieval=true`가 필요합니다.

```properties
DB_URL=jdbc:mariadb://DB_HOST:3306/Likelion_SYU_festa2026?allowPublicKeyRetrieval=true
```

이 옵션은 접속 시 서버에서 공개키를 받아오므로 네트워크 중간자 공격 방지를 위해 운영 환경에서는 DB TLS(`sslMode=verify-full`) 또는 신뢰한 `serverRsaPublicKeyFile` 설정을 우선 권장합니다.

SSO OAuth Client 권장 설정:

- Grant types: `authorization_code`, `refresh_token`, `client_credentials`
- Scopes: `openid`, `email`, `profile`, `user.email.read`, `user.profile.read`

현재 사용자 로그인 구현은 축제 백엔드가 SSO의 `/api/auth/login`과 `/api/auth/token/refresh`를 프록시하는 방식이며 OAuth callback 엔드포인트는 없습니다. `client_credentials`는 QR 스캔 등 서버 간 사용자 프로필 조회에 사용합니다.

## REST API

### 인증

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| POST | `/api/auth/signup/email/send` | 없음 | 회원가입 이메일 인증번호 발송 |
| POST | `/api/auth/signup/email/verify` | 없음 | 회원가입 이메일 인증번호 확인 |
| POST | `/api/auth/signup` | 없음 | SSO 회원가입 |
| POST | `/api/auth/login` | 없음 | 로그인 및 토큰 발급 |
| POST | `/api/auth/email/send` | 없음 | 아이디 찾기·비밀번호 재설정 인증번호 발송 |
| POST | `/api/auth/email/find-id/verify` | 없음 | 이메일 인증 후 로그인 아이디 반환 |
| POST | `/api/auth/email/reset-password/verify` | 없음 | 인증번호 확인과 새 비밀번호 적용 |
| POST | `/api/auth/token/refresh` | Refresh 쿠키 | Access Token 갱신 |
| POST | `/api/auth/logout` | 선택 | SSO 로그아웃 및 로컬 Refresh 쿠키 삭제 |

계정 복구 API의 요청 예시와 React 연동 시 주의사항은 [로그인 전 계정 복구 API 가이드](docs/frontend-account-recovery-api.md)를 참고하세요. 비밀번호 재설정은 별도 reset token 없이 인증번호와 새 비밀번호를 한 요청으로 처리합니다.

### 내 정보와 계정

| Method | Path | 설명 |
|---|---|---|
| GET | `/api/users/me` | SSO 내 정보와 축제 역할 조회 |
| PATCH | `/api/users/me/profile` | 이름·전화번호 수정 |
| POST | `/api/users/me/email/verification` | 새 이메일 인증번호 발송 |
| POST | `/api/users/me/email/verification/confirm` | 새 이메일 인증번호 확인 |
| PATCH | `/api/users/me/email` | 인증된 이메일로 변경 |
| PATCH | `/api/users/me/password` | SSO 비밀번호 변경 후 토큰 제거 |
| DELETE | `/api/users/me` | 축제 사용자만 삭제하는 것이 아니라 SSO 계정 자체 탈퇴 |

모든 API는 Bearer 인증이 필요합니다.

로그인 사용자의 비밀번호 변경은 현재 비밀번호와 Bearer Access Token을 함께 검증합니다. 성공 시 Refresh 쿠키를 삭제하므로 React는 메모리 Access Token과 사용자 캐시를 비운 뒤 로그인 화면으로 이동해야 합니다. 자세한 예시는 [로그인 사용자 비밀번호 변경 가이드](docs/frontend-password-change-api.md)를 참고하세요.

### 공연팀

| Method | Path | 권한 | 설명 |
|---|---|---|---|
| GET | `/api/performances` | `USER` 이상 | 공개 시각이 지난 공연 목록 |
| GET | `/api/performances/{id}` | `USER` 이상 | 공개된 공연 상세 |
| POST | `/api/performances` | `ADMIN` 이상 | 공연팀과 링크 등록 |
| PATCH | `/api/performances/{id}` | `ADMIN` 이상 | 공연팀 정보 수정 |
| POST | `/api/performances/{id}/images` | `ADMIN` 이상 | 이미지 파일 추가 |
| POST | `/api/performances/{id}/videos` | `ADMIN` 이상 | 동영상 파일 추가 |
| DELETE | `/api/performances/{id}/media/{mediaId}` | `ADMIN` 이상 | 이미지·동영상 삭제 |
| DELETE | `/api/performances/{id}` | `ADMIN` 이상 | 공연팀 삭제 |

공연 구분은 `CELEBRITY`, `CLUB`, `INDIVIDUAL`입니다. 링크는 최대 3개이고 이미지와 동영상은 각각 링크와 파일을 합해 최대 3개입니다. 업로드 파일은 Cloudflare R2에 저장하며 기본 제한은 이미지 10MB, 동영상 200MB입니다.

공용 R2 버킷에서 다른 서비스와 경로가 충돌하지 않도록 공연 미디어는 기본적으로 `festa2026_performance/images/`, `festa2026_performance/videos/` prefix 아래에 저장합니다. 운영 환경에서 `R2_PERFORMANCE_PREFIX`를 지정하면 이 값을 덮어쓸 수 있습니다.

### 동적 QR

| Method | Path | 권한 | 설명 |
|---|---|---|---|
| POST | `/api/qr/tokens` | `USER` 이상 | 내 동적 QR 토큰 발급 |
| POST | `/api/qr/scan` | `BOOTH_MANAGER` 이상 | QR 토큰으로 권한별 사용자 정보 조회 |

QR에는 사용자 정보나 Access Token을 넣지 않습니다. 서버는 256비트 난수 토큰을 생성하고 SHA-256 해시, `userUuid`, 만료 시각만 `festival_qr_tokens`에 저장합니다. 기본 유효시간은 60초이고 기존 QR은 각자의 만료 시각까지 유지됩니다.

QR 조회 응답 범위:

- `BOOTH_MANAGER`: 마스킹된 이름과 학번, 학과, 학년
- `STAFF`: 이름, 학번, 학과, 학년
- `ADMIN`: STAFF 정보와 전화번호, 이메일
- `SUPER_ADMIN`: SSO 전체 프로필과 축제 역할
- `USER`: 스캔 불가

타 사용자 개인정보는 SSO `client_credentials`와 `/api/internal/users/profiles/batch`로 조회하며 축제 DB에 복사하지 않습니다.

### React 프런트 이벤트

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| POST | `/api/analytics/events` | 선택 | 로그인·비로그인 페이지 방문 및 주요 행동 이벤트 최대 20개 일괄 수집 |

- 비로그인 요청은 `userUuid = null`로 저장합니다.
- 정상 Bearer Token이 있으면 SSO에서 검증한 `userUuid`를 연결합니다.
- 잘못된 Bearer Token은 익명으로 우회하지 않고 `401`을 반환합니다.
- 이벤트는 메모리 큐를 거쳐 최대 500건씩 JDBC Batch로 저장합니다.
- 동일 `eventId` 재전송은 중복 저장하지 않습니다.
- 기본 세션 제한은 분당 120개 이벤트입니다.
- 전체 URL, 쿼리 문자열, 폼 값과 임의 metadata는 받지 않습니다.

React Router 연동, 이벤트 종류와 재시도 예제는 [프런트 이벤트 로깅 연동 가이드](docs/frontend-analytics-api.md)를 참고하세요.

### React 접속 현황 heartbeat

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| POST | `/api/presence/heartbeat` | 없음 | 익명 브라우저 세션의 현재 route와 마지막 활동 시각 갱신 |

heartbeat는 DB에 저장하지 않고 JVM 메모리에서 기본 150초 TTL로만 유지합니다. 관리자 화면의 접속자 수는 개인정보나 로그인 사용자 목록이 아니라 최근 heartbeat가 있는 활성 탭의 추정치입니다. React 연동 코드는 [접속 현황 heartbeat 가이드](docs/frontend-presence-api.md)를 참고하세요.

## 관리자 페이지

관리자 페이지는 Thymeleaf로 제공됩니다.

| Method | Path | 설명 |
|---|---|---|
| GET/POST | `/admin/login` | 관리자 로그인 화면과 로그인 처리 |
| POST | `/admin/logout` | 관리자 로그아웃 |
| GET | `/admin` | 관리자 대시보드 |
| GET | `/admin/qr` | 카메라 QR 스캔 화면 |
| POST | `/admin/qr/scan` | QR 토큰 조회 |
| GET | `/admin/performances` | 공연팀 목록 |
| GET | `/admin/performances/new` | 공연팀 등록 화면 |
| POST | `/admin/performances` | 공연팀 등록 |
| GET | `/admin/performances/{id}/edit` | 공연팀 수정 화면 |
| POST | `/admin/performances/{id}` | 공연팀 수정 |
| POST | `/admin/performances/{id}/delete` | 공연팀 삭제 |
| GET | `/admin/system` | `SUPER_ADMIN` 전용 실시간 시스템 모니터링 GUI |
| GET | `/admin/system/snapshot` | 모니터링 화면용 5초 갱신 JSON |

QR 관리 화면은 `BOOTH_MANAGER` 이상이 사용할 수 있고 공연팀 관리는 `ADMIN`, `SUPER_ADMIN`만 사용할 수 있습니다. 시스템 모니터링은 축제 DB 역할이 정확히 `SUPER_ADMIN`인 사용자만 접근할 수 있습니다.

## 실시간 시스템 모니터링

관리자 GUI는 접속 규모, 최근 1분 HTTP 요청·오류·지연, JVM heap·스레드, CPU·디스크, HikariCP 연결 풀, MariaDB 상태와 비동기 로그 큐를 표시합니다.

- GUI는 5초마다 갱신하지만 MariaDB 상태 조회는 서버 전체에서 기본 15초에 한 번만 실행하고 메모리 캐시를 공유합니다.
- 모니터링 스냅샷 자체는 MariaDB에 저장하지 않습니다.
- `SHOW GLOBAL STATUS` 권한이 없으면 DB 연결 상태만 표시하고 상세 항목을 제한 상태로 표시합니다.
- 최근 5분 그래프 이력은 해당 관리자 브라우저 메모리에만 유지됩니다.
- Actuator는 기본 `127.0.0.1:9091` 별도 포트에서 `health`, `prometheus`만 노출하며 공개 관리자 포트로 프록시하지 않습니다.
- 단일 애플리케이션 인스턴스 기준입니다. 다중 인스턴스에서는 Prometheus/Grafana 등 외부 집계가 필요합니다.

## API 요청 로깅

`/api/**`, `/admin`, `/admin/**` 요청은 `api_request_logs`에 비동기로 기록됩니다. 정적 파일, Swagger와 `OPTIONS` 요청은 제외됩니다.

기록 필드:

- `requestId`, 검증된 `userUuid`
- 클라이언트 IP, HTTP method, 실제 path와 route pattern
- 응답 상태, 처리 시간, Host, scheme, User-Agent, 기록 시각

처리 정책:

- 요청 스레드는 DB INSERT를 기다리지 않습니다.
- 기본 큐 10,000건, 최대 250건 JDBC Batch, 250ms flush를 사용합니다.
- 관리자·쓰기·오류·1초 이상 요청은 전부 기록합니다.
- 정상 `GET /api/**`는 기본 25% 표본 기록합니다.
- `X-Request-ID`를 응답하고 같은 값을 SSO `X-Correlation-ID`로 사용합니다.
- 비밀번호, 인증번호, Authorization, Cookie, 토큰, 요청 본문과 쿼리 문자열은 저장하지 않습니다.
- 로그는 기본 60일 후 작은 배치로 삭제합니다.
- 프록시 헤더 신뢰는 기본 비활성화입니다. 운영 환경에서는 `API_REQUEST_LOG_TRUST_FORWARDED_HEADERS=true`로 활성화하고, Nginx가 외부 전달 헤더를 제거한 뒤 다시 설정해야 합니다.

실제 클라이언트 IP를 안전하게 기록하기 위한 Nginx 예시:

```nginx
location / {
    proxy_pass http://127.0.0.1:8888;

    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $remote_addr;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-Host $host;
    proxy_set_header Forwarded "";
}
```

이 서비스는 `X-Forwarded-For`의 첫 번째 주소를 기록하므로 `$proxy_add_x_forwarded_for` 대신 `$remote_addr`로 덮어써야 클라이언트가 임의로 보낸 헤더에 의한 IP 위조를 방지할 수 있습니다. Spring Boot 포트 `8888`도 외부에 직접 공개하지 않고 Nginx 또는 로컬 인터페이스를 통해서만 접근시키는 것을 권장합니다.

API 요청 로그와 React 화면 이벤트 로그는 서로 다른 테이블과 큐를 사용합니다.

## 주요 DB 테이블

```text
festival_users
festival_roles
festival_qr_tokens
festival_performances
festival_performance_members
festival_performance_links
festival_performance_media
api_request_logs
frontend_event_logs
```

Hibernate `ddl-auto=update` 설정으로 필요한 테이블과 인덱스를 생성합니다.

## Swagger / OpenAPI

- Swagger UI: `http://localhost:8888/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8888/v3/api-docs`
- OpenAPI YAML: `http://localhost:8888/v3/api-docs.yaml`

Swagger UI의 **Authorize** 버튼에는 SSO Access Token 원문만 입력합니다. `Bearer ` 접두사는 Swagger UI가 자동으로 추가합니다. `/api/analytics/events`는 비로그인 호출도 가능하므로 Swagger에서 Bearer 인증 표시가 없습니다.

## 실행과 테스트

```powershell
.\gradlew.bat bootRun
```

```powershell
.\gradlew.bat test
```

대부분의 통합 테스트는 로컬 가짜 SSO와 테스트 DB를 이용해 다음을 검증합니다.

- 회원가입, nullable 프로필, 로그인 실패와 토큰 rotation
- 로그아웃, 이메일·비밀번호 변경, SSO timeout
- 축제 역할 분리와 QR 권한별 마스킹
- 공연 공개 시각, ADMIN 쓰기 권한과 미디어 제한
- API 요청 로그 필터와 JDBC Batch
- 로그인·비로그인 프런트 이벤트, 중복·시간·rate limit 검증

기본 `Festa2026ApplicationTests.contextLoads()`는 현재 설정된 MariaDB에 연결하므로 DB가 실행 중이지 않으면 전체 테스트 명령에서 해당 테스트가 실패할 수 있습니다.
