# Swagger로 API 연동 확인하기

[문서 목차](README.md) · [연동 시작하기](frontend-getting-started.md) · [API 색인](api-endpoint-index.md)

## 1. 문서 열람과 API 인증은 별개

| 목적 | 인증 방식 | 시작 경로 |
|---|---|---|
| OpenAPI JSON·타입 생성 | 인증 없음 | `GET /admin/v3/api-docs` |
| Swagger UI 열람 | `/admin` 전용 HttpOnly 쿠키 | `/admin/swagger-ui.html` → `/admin/login?next=swagger` |
| 사용자 REST API 실행 | SSO Access Token을 Bearer 헤더로 전송 | `POST /api/auth/login` |
| 사용자 Access Token 갱신 | `/api` 전용 Refresh 쿠키 | `POST /api/auth/token/refresh` |
| 관리자 HTML 폼 | 관리자 쿠키와 CSRF | `/admin/login` |

Swagger UI 열람은 축제 권한 `ADMIN`, `SUPER_ADMIN`만 허용합니다. `USER`, `BOOTH_MANAGER`, `STAFF`만 보유한 사용자는 접근할 수 없습니다. 문서에 접근할 수 있어도 개별 API의 학생 인증 등 추가 조건을 통과한 것은 아닙니다. 허용 목록은 `AdminAccessService.SWAGGER_ALLOWED_ROLES`에서 관리합니다.

로그인 화면과 인증 처리는 기존 관리자 로그인 하나를 사용합니다. `next=swagger`는 로그인 후 Swagger로 돌아가기 위한 값입니다. 이미 관리자에 로그인했다면 다시 로그인할 필요가 없습니다. STAFF·BOOTH_MANAGER는 관리자 로그인은 가능하지만 Swagger 접근 시 403으로 거부됩니다.

명세 JSON의 `GET /admin/v3/api-docs`는 로그인 없이 조회할 수 있습니다. 프론트 `npm run gen:api`의 스펙 주소는 `https://festa.syu-likelion.org/admin/v3/api-docs`로 설정합니다. YAML(`/admin/v3/api-docs.yaml`)과 UI 설정(`/admin/v3/api-docs/swagger-config`), Swagger UI는 기존 관리자 인증이 필요하며 미인증 요청은 401(UI는 로그인 이동), 권한 부족은 403입니다. 이전 `/swagger-ui.html`, `/swagger-ui/index.html`, `/v3/api-docs` 경로는 제공하지 않습니다.

## 2. 실제 테스트 순서

1. Swagger에 로그인하고 **Servers**에서 테스트할 백엔드를 고릅니다. 로컬 데이터와 운영 데이터는 별개입니다. 로컬에서 `localhost`와 `127.0.0.1`을 혼용하면 쿠키가 공유되지 않습니다.
2. `POST /api/auth/login`을 펼쳐 **Try it out**으로 로그인합니다. `reactivate`는 생략하거나 `false`로 보냅니다. 409 복구 동의가 필요한 경우 [인증 문서](frontend-auth-api.md)의 절차를 따릅니다.
3. 성공 JSON의 `accessToken`을 복사해 **Authorize → bearerAuth**에 원문만 넣습니다. `Bearer `는 UI가 붙입니다.
4. `GET /api/users/me`로 연결과 축제 권한·학생 인증 상태를 확인한 뒤 원하는 기능을 호출합니다.
5. 토큰이 갱신되면 응답의 `X-Access-Token` 또는 갱신 API의 `accessToken`으로 Authorize 값을 직접 교체합니다. Swagger가 응답 값을 자동 반영하지는 않습니다.

Refresh Token은 API 로그인 응답의 HttpOnly 쿠키이며 브라우저가 저장·전송합니다. 쿠키 인증 스키마는 요구 조건을 설명하기 위한 것입니다. Swagger 입력칸이나 JavaScript로 HttpOnly 쿠키를 직접 설정하지 않습니다. 문서 열람용 관리자 로그인만으로 API Refresh 쿠키가 발급되지는 않습니다.

Swagger에서는 가능한 한 열람 중인 백엔드와 같은 서버를 선택합니다. 다른 Origin 호출은 CORS·쿠키의 SameSite/Secure 조건을 추가로 만족해야 합니다. 실제 프런트에서는 [공통 fetch 규약](frontend-api-common.md)의 `credentials: "include"`를 적용합니다.

## 3. 화면별 접근 조건

| 기능 | 사용자 조회 조건 | 추가 조건 |
|---|---|---|
| 일반 공지·분실물·협찬사 | 공개 조회 가능 | 등록·수정은 각 도메인의 관리자 권한 |
| 부스 | 공개 조회 가능 | 찜은 로그인, 관리는 별도 권한 |
| 공연·타임테이블 | 일반 목록·상세는 비로그인 가능 | 공개 시각에 따라 정보 필터링, 관리자 조회·관리는 ADMIN 이상 |
| 대나무숲·생일축하·투표/설문 | 로그인 + 학생 인증 | 조회도 동일. 학생회비 납부는 필수 아님 |
| 본인 팔찌 상태 | 로그인 | 지급은 학생 인증 필수, 현장 처리는 관리자 웹 |

이는 사용자 조회의 요약입니다. 상세 권한은 기능별 문서를 따릅니다. 보호 API는 Refresh 쿠키만으로 직접 호출할 수 없으며 Bearer를 먼저 확보해야 합니다.

## 4. 스키마를 읽을 때

- `required`와 길이·enum은 기본 입력 검증입니다. 학교 세션, 시간 범위, 중복, 권한 등 서비스 검증도 적용됩니다.
- 가입·아이디 중복 확인은 Unicode 코드 포인트 4–50자입니다. 기존 로그인에 가입 시점 검증을 그대로 적용하지 않습니다.
- 비밀번호 가입·재설정 조건과 로그인·로그인 후 비밀번호 변경 조건은 각각의 문서를 따릅니다.
- `null`과 필드 생략을 구분합니다. 예를 들어 QR 조회에서 권한으로 생략된 납부 여부는 `false`가 아닙니다.
- 채팅 `HIDDEN`은 원문을 제공합니다. `BLOCKED`·`DELETED`는 원문이 null이므로 기존 화면의 본문도 제거합니다.
- 배열·페이지 객체·커서 응답은 기능마다 다릅니다. `204`와 본문 없는 `200`은 JSON 파싱하지 않습니다.
- `default` 오류 응답은 공통 오류 형식의 설명이며 모든 HTTP 상태가 모든 API에서 발생한다는 뜻은 아닙니다. 프록시·보안 필터의 오류는 JSON이 아닐 수도 있습니다.
- Swagger **Example Value**는 스키마에서 만든 예시입니다. 실제 권한별 필드 생략·공개 전 상태는 실행 응답과 기능별 예시를 확인합니다.

## 5. 404 또는 로그인 문제가 생기면

- 백엔드에 `/admin/swagger-ui.html`과 `/admin/v3/api-docs` 경로가 포함된 최신 빌드가 배포되었는지 확인합니다.
- 프록시가 `/admin/**`를 백엔드로 전달하는지 확인합니다. 주소 수정만으로 배포된 구버전 코드가 바뀌지는 않습니다.
- 문서 로그인 성공과 API 로그인 성공을 구분하고, 선택한 Servers의 호스트와 쿠키 범위를 확인합니다.
- 최종 401은 재로그인합니다. 403은 응답 code로 역할 부족·학생 미인증·기능 제한을 구분합니다.

로컬 문서 검증은 `OpenApiDocumentationIntegrationTests`로 실행합니다. 생성 결과는 `build/reports/openapi.json`이며 운영 배포 여부를 확인하는 테스트는 아닙니다.
