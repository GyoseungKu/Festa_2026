# Festa_2026 Backend

삼육대학교 2026 천보축전 서비스의 Spring Boot 백엔드입니다.

사용자 인증과 개인정보 원본은 기존 SSO에 위임합니다. 이 애플리케이션은 SSO의 `userUuid`를 기준으로 축제 전용 권한, 부스·스탬프·공연·분실물·생일축하 쪽지와 운영 로그를 관리합니다.

## 주요 기능

- SSO 회원가입·로그인·토큰 갱신·계정 관리 중계
- 학교 SSO 기반 학생 인증, 학적정보 비교와 관리자 승인
- 위도·경도 기반 부스 지도, 상세 정보, 찜
- 부스 이미지 최대 5개, 동영상 최대 3개, 통합 정렬 및 대표 미디어 설정
- 담당 부스 기반 QR 스탬프 지급·회수와 감사 이력
- 공연팀과 공개 일정, 링크·이미지·동영상 관리
- 분실물 공지, 사진, 반환 상태, 상단 고정과 조회수
- 생일축하 쪽지, 하트와 권한별 작성자 조회
- 로그인 사용자용 대나무숲 익명 채팅, 신고·작성 제한과 차단 감사 이력
- Thymeleaf 관리자 페이지와 실시간 운영 모니터링
- API 요청 로그, 프런트 이벤트 로그와 익명 접속 heartbeat

## 기술 구성

- Java 21
- Spring Boot 4.1, Spring MVC, Security, Validation, Thymeleaf
- Spring Data JPA, MySQL/MariaDB
- Cloudflare R2 및 AWS SDK for Java 2.x
- Springdoc OpenAPI 3
- Micrometer, Actuator, Prometheus
- Embedded Tomcat 11.0.24
- Gradle Wrapper

애플리케이션 기본 포트는 `8888`, 로컬 전용 Actuator 포트는 `9091`입니다.

## 인증 구조

```text
React 사용자 페이지
  -> Authorization: Bearer {SSO Access Token}
  -> Festa Backend
  -> SSO

Thymeleaf 관리자 페이지
  -> /admin 전용 HttpOnly Access/Refresh 쿠키
  -> Festa Backend
  -> SSO
```

- 로그인 응답의 Access Token은 JSON으로 반환합니다. 프런트에서는 메모리에 보관하고 `localStorage`에는 저장하지 않는 방식을 권장합니다.
- 사용자 Refresh Token은 `festivalRefreshToken` HttpOnly 쿠키로 중계합니다.
- 보호된 REST API는 `Authorization: Bearer <accessToken>`을 사용합니다.
- Access Token 만료 시 백엔드가 Refresh Token으로 한 번 갱신하여 요청을 재시도할 수 있습니다.
- 갱신된 Access Token은 `X-Access-Token` 응답 헤더에 담깁니다.
- 쿠키를 사용하는 React 요청은 `credentials: "include"`가 필요합니다.
- 관리자 페이지는 `festivalAdminAccess`, `festivalAdminRefresh` HttpOnly 쿠키를 사용하며 서버 세션은 사용하지 않습니다.
- 비밀번호, Access/Refresh Token과 SSO 개인정보 원본은 축제 DB에 저장하지 않습니다.

`/api/**`는 Bearer 인증을 사용하므로 CSRF 검사에서 제외됩니다. `/admin/**` 폼 요청은 CSRF 보호를 적용합니다.

## 축제 권한

SSO의 `ssoRole`과 축제 운영 권한은 별개입니다.

`festival_users.management_role`은 다음 값 중 하나를 갖는 ENUM입니다.

| 역할 | 주요 권한 |
|---|---|
| `SUPER_ADMIN` | 전체 관리 및 시스템 모니터링 |
| `ADMIN` | 부스·스탬프·공연·분실물·생일축하·대나무숲 설정·참여자 차단 관리 |
| `STAFF` | 분실물·생일축하·대나무숲 메시지 운영 및 마스킹된 사용자 조회 |
| `USER` | 일반 사용자 기능 |

부스 관리자 여부는 별도 `festival_users.booth_manager` boolean과 `festival_booth_managers` 담당 부스 관계로 관리합니다. 따라서 한 사용자가 `ADMIN`이면서 동시에 특정 부스의 `BOOTH_MANAGER`일 수 있습니다. 관리 권한 판정에서는 `SUPER_ADMIN`, `ADMIN`을 우선합니다.

처음 연결된 사용자는 기본 `USER`로 생성됩니다. 담당 부스에 지정되면 `booth_manager=true`가 되고, 더 이상 담당 부스가 없으면 자동 해제됩니다.

## 로컬 실행

### 요구사항

- JDK 21
- MySQL 8 또는 MariaDB
- 사용 가능한 SSO OAuth Client
- 학생 인증을 사용할 경우 학교 SSO OAuth Client와 JWKS 엔드포인트
- Cloudflare R2 버킷

### 환경설정

[env.properties.example](env.properties.example)을 복사하여 프로젝트 루트에 `env.properties`를 만듭니다.

```powershell
Copy-Item env.properties.example env.properties
```

`env.properties`는 Git에서 제외되고 JAR에도 포함되지 않습니다. 운영 환경에서는 서버 환경변수나 Secret Manager 사용을 권장합니다.

필수값은 DB 계정, SSO Client, R2 자격증명과 메일 계정입니다. `DB_URL`도 배포 환경에 맞게 명시적으로 설정하십시오.

학교 학생 인증을 활성화하려면 `SCHOOL_SSO_ENABLED=true`와 `SYU_SSO_CLIENT_ID`, `SYU_SSO_CLIENT_SECRET`, `SYU_SSO_SUBJECT_HASH_SECRET` 및 학교 SSO URL·Issuer·Audience 설정이 필요합니다. 학번 연결 해시는 별도의 16바이트 이상 `SYU_SSO_SUBJECT_HASH_SECRET` 사용을 권장하며, 비어 있으면 학교 SSO Client Secret을 대신 사용합니다. 전체 변수는 [env.properties.example](env.properties.example)과 [학생 인증 기능 문서](docs/student-verification.md)를 확인합니다.

MySQL `caching_sha2_password` 계정을 TLS 없이 사용하는 개발 환경에서는 JDBC URL에 `allowPublicKeyRetrieval=true`가 필요할 수 있습니다. 운영 환경에서는 DB TLS 또는 신뢰한 RSA 공개키 파일을 우선 사용합니다.

### 실행

```powershell
.\gradlew.bat bootRun
```

빌드된 JAR 실행:

```powershell
.\gradlew.bat bootJar
java -jar build\libs\Festa_2026-0.0.1-SNAPSHOT.jar
```

## CORS

기본 허용 Origin은 다음 두 개입니다.

```text
http://localhost:5173
https://festa.syu-likelion.org
```

환경변수로 덮어쓸 수 있습니다.

```properties
FRONTEND_ORIGINS=http://localhost:5173,https://festa.syu-likelion.org
```

Origin은 경로나 마지막 `/` 없이 `scheme://host[:port]` 형식으로 입력합니다. Thymeleaf 관리자 페이지와 API가 같은 도메인에서 제공되면 관리자 페이지 요청은 same-origin이므로 별도 관리자용 CORS 설정이 필요하지 않습니다.

허용 메서드는 `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`이며, 자격증명 요청을 허용합니다.

## REST API

세부 요청·응답 스키마는 Swagger UI와 `docs/`의 프런트 연동 문서를 기준으로 확인할 수 있습니다.

### 인증

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| POST | `/api/auth/signup/email/send` | 없음 | 회원가입 이메일 인증번호 발송 |
| POST | `/api/auth/signup/email/verify` | 없음 | 회원가입 이메일 인증번호 확인 |
| POST | `/api/auth/signup` | 없음 | SSO 회원가입 |
| GET | `/api/auth/check/login-id` | 없음 | 로그인 아이디 중복 확인 |
| GET | `/api/auth/check/email` | 없음 | 이메일 중복 확인 |
| GET | `/api/auth/check/student-no` | 없음 | 학번 중복 확인 |
| GET | `/api/auth/check/phone` | 없음 | 전화번호 중복 확인 |
| GET | `/api/auth/school/authorize` | 없음 | 학교 SSO 학적정보 인증 시작(Redirect) |
| GET | `/api/auth/school/profile` | 학교 SSO 세션 | 검증된 이름·학번·학과 조회 |
| DELETE | `/api/auth/school/profile` | 학교 SSO 세션 | 임시 학적정보 폐기 |
| POST | `/api/auth/login` | 없음 | 로그인 및 토큰 발급 |
| POST | `/api/auth/email/send` | 없음 | 아이디 찾기·비밀번호 재설정 인증번호 발송 |
| POST | `/api/auth/email/find-id/verify` | 없음 | 아이디 찾기 인증번호 확인 |
| POST | `/api/auth/email/reset-password/verify` | 없음 | 비밀번호 재설정 |
| POST | `/api/auth/token/refresh` | Refresh 쿠키 | Access Token 갱신 |
| POST | `/api/auth/logout` | 선택 | SSO 로그아웃 및 Refresh 쿠키 삭제 |

회원가입 비밀번호는 8~20자이며 영문 대문자·소문자·숫자·특수문자를 각각 1개 이상 포함해야 합니다. 공백을 제외한 ASCII 출력 문자(U+0021~U+007E)만 허용하며 trim하지 않습니다. 로그인·비밀번호 변경·재설정은 기존 정책을 유지합니다.

회원가입의 `loginId`, `password`, `email`, `name`, `studentNo`, `department`는 필수이고 `phone`, `grade`, `enrollment`, `birthDate`는 nullable입니다. `academicInfoSource`는 직접입력 `MANUAL`(기본값) 또는 학교 SSO 자동입력 `SCHOOL_SSO`이며, 학교 방식에서는 서버가 RS256 검증을 마친 이름·학번·학과로 요청값을 강제 교체합니다.

### HTML 가입 환영 메일

- Festa를 통해 SSO 신규가입을 완료하거나 기존 SSO 사용자가 Festa에 최초 연결되면 HTML 환영 메일을 비동기로 발송합니다.
- 신규 Festa 연결 시에만 발송 대기 상태를 만들고 완료 상태를 `festival_users`에 저장하므로, 기존 Festa 이용자나 이후 로그인에는 중복 발송하지 않습니다.
- SMTP 또는 템플릿 처리 실패는 가입·로그인을 실패시키지 않으며 처리 상태를 해제하여 다음 로그인에서 재시도합니다.
- 제목 기본값은 `[2026 천보축전] 회원가입 완료 안내`입니다.
- HTML은 [welcome.html](src/main/resources/templates/mail/welcome.html)에서 수정합니다.

| 환경변수 | 기본값/설명 |
|---|---|
| `WELCOME_EMAIL_ENABLED` | `true`, 환영 메일 기능 활성화 |
| `WELCOME_EMAIL_FROM` | `no-reply@syu-likelion.org`; Gmail에서는 인증 계정 또는 등록된 발신 별칭으로 승인 필요 |
| `WELCOME_EMAIL_FROM_NAME` | `Likelion SYU`, 발신자 표시 이름 |
| `WELCOME_EMAIL_SUBJECT` | 환영 메일 제목 |
| `WELCOME_EMAIL_SITE_URL` | `https://festa.syu-likelion.org` |

### 내 정보와 계정

아래 API는 모두 Bearer 인증이 필요합니다.

| Method | Path | 설명 |
|---|---|---|
| GET | `/api/users/me` | SSO 내 정보와 축제 역할 조회 |
| POST | `/api/users/me/school-verification/authorize` | 가입 후 학교 학생 인증 URL 발급 |
| GET | `/api/users/me/school-verification/department` | 학교·회원 학과 불일치 내용 조회 |
| POST | `/api/users/me/school-verification/department/confirm` | 학교 학과로 회원정보 수정 후 인증 |
| PATCH | `/api/users/me/profile` | 전화번호·학과·학년·재학 상태 수정 |
| POST | `/api/users/me/email/verification` | 새 이메일 인증번호 발송 |
| POST | `/api/users/me/email/verification/confirm` | 새 이메일 인증번호 확인 |
| PATCH | `/api/users/me/email` | 인증된 이메일로 변경 |
| PATCH | `/api/users/me/password` | SSO 비밀번호 변경 후 인증 쿠키 제거 |
| DELETE | `/api/users/me` | SSO 계정 탈퇴 |

이름·학번 불일치 인증은 `/api/admin/school-verifications`에서 `SUPER_ADMIN`만 목록 조회, 승인, 삭제할 수 있습니다. 인증된 사용자가 프로필 API로 학과를 수정하면 학생 인증 상태는 `REVOKED`가 되며 기존 학교 토큰 확인 시각은 유지됩니다. 관리자 승인은 축제 인증 상태만 변경하며 동아리 SSO 회원정보를 수정하지 않습니다.

현재 미승인 요청에는 자동 만료·정리와 회원 탈퇴 연계가 없고, 동아리 SSO 변경과 축제 DB 저장도 하나의 분산 트랜잭션이 아닙니다. 운영 적용 전 필요한 보완 사항과 정확한 상태·시각 의미는 [학생 인증 기능 문서](docs/student-verification.md)를 기준으로 확인합니다.

### 부스 지도와 찜

| Method | Path | 권한 | 설명 |
|---|---|---|---|
| GET | `/api/booths` | 공개 | 지도에 표시할 전체 부스와 로그인 사용자의 찜 여부 |
| GET | `/api/booths/{id}` | 공개 | 부스 상세와 정렬된 이미지·동영상 |
| GET | `/api/users/me/favorite-booths` | 로그인 | 내가 찜한 부스 목록 |
| POST | `/api/booths/{id}/favorite` | 로그인 | 찜 등록 |
| DELETE | `/api/booths/{id}/favorite` | 로그인 | 찜 해제 |
| POST | `/api/booths` | `ADMIN` 이상 | 부스 등록 |
| PATCH | `/api/booths/{id}` | `ADMIN` 이상 | 좌표·운영 정보·스탬프 여부·관리자 수정 |
| POST | `/api/booths/{id}/images` | `ADMIN` 이상 | 이미지 파일 업로드, 최대 5개 |
| POST | `/api/booths/{id}/videos` | `ADMIN` 이상 | 동영상 파일 업로드, 최대 3개 |
| PATCH | `/api/booths/{id}/media/order` | `ADMIN` 이상 | 이미지·동영상 통합 순서와 대표 미디어 설정 |
| DELETE | `/api/booths/{id}/media/{mediaId}` | `ADMIN` 이상 | 미디어 삭제 |
| DELETE | `/api/booths/{id}` | `ADMIN` 이상 | 부스와 찜·미디어 삭제 |

부스는 위도·경도, 이름, 운영 주체, 설명, 하루 기준 시작·종료 시각, 스탬프 지급 여부와 여러 명의 담당 관리자를 가집니다. 대표 미디어는 첫 업로드 항목으로 자동 지정되며 이후 정렬 API나 관리자 페이지에서 변경할 수 있습니다.

### 스탬프

| Method | Path | 권한 | 설명 |
|---|---|---|---|
| GET | `/api/users/me/stamps` | 로그인 | 내 스탬프판 조회 |
| POST | `/api/booths/{boothId}/stamps/qr/lookup` | 담당 `BOOTH_MANAGER`, `ADMIN` 이상 | QR 사용자와 현재 지급 상태 조회 |
| POST | `/api/booths/{boothId}/stamps/qr/grant` | 담당 `BOOTH_MANAGER`, `ADMIN` 이상 | QR로 지급 |
| POST | `/api/booths/{boothId}/stamps/qr/revoke` | 담당 `BOOTH_MANAGER`, `ADMIN` 이상 | QR로 회수 |
| POST | `/api/booths/{boothId}/stamps/users/{userUuid}/grant` | `ADMIN` 이상 | 사용자 검색으로 임의 지급 |
| POST | `/api/booths/{boothId}/stamps/users/{userUuid}/revoke` | `ADMIN` 이상 | 사용자 검색으로 임의 회수 |
| GET | `/api/booths/{boothId}/stamps/history` | 담당 `BOOTH_MANAGER`, `ADMIN` 이상 | 현재 보유자와 지급·회수 감사 이력 |

- 한 사용자는 한 부스에서 현재 스탬프를 최대 하나만 보유할 수 있습니다.
- 스탬프판은 축제 기간 중 한 번 참여하며 회차나 초기화 개념이 없습니다.
- 회수 후 재지급할 수 있고 모든 지급·회수는 감사 이력에 남습니다.
- `BOOTH_MANAGER`는 배정된 스탬프 지급 부스만 선택할 수 있고 QR 방식만 사용합니다. 사용자 UUID와 이름·학번 등은 제한 또는 마스킹됩니다.
- `ADMIN`, `SUPER_ADMIN`은 모든 스탬프 지급 부스를 선택하고 QR 또는 사용자 검색으로 처리할 수 있습니다.
- `STAFF`는 스탬프 관리 권한이 없습니다.
- 이력 API는 `page=0`, `size=30`이 기본이며 최대 크기는 100입니다. 현재 보유자 목록은 전체, 감사 이력은 최신순 페이지 단위로 반환합니다.

### 공연팀

| Method | Path | 권한 | 설명 |
|---|---|---|---|
| GET | `/api/performances` | 로그인 | 공개 시각이 지난 공연 목록 |
| GET | `/api/performances/{id}` | 로그인 | 공개된 공연 상세 |
| POST | `/api/performances` | `ADMIN` 이상 | 공연팀과 링크 등록 |
| PATCH | `/api/performances/{id}` | `ADMIN` 이상 | 공연팀 정보 수정 |
| POST | `/api/performances/{id}/images` | `ADMIN` 이상 | 이미지 파일 추가 |
| POST | `/api/performances/{id}/videos` | `ADMIN` 이상 | 동영상 파일 추가 |
| DELETE | `/api/performances/{id}/media/{mediaId}` | `ADMIN` 이상 | 미디어 삭제 |
| DELETE | `/api/performances/{id}` | `ADMIN` 이상 | 공연팀 삭제 |

공연 구분은 `CELEBRITY`, `CLUB`, `INDIVIDUAL`입니다. 일반 링크는 최대 3개이고 이미지와 동영상은 각각 링크와 파일을 합해 최대 3개입니다.

### 투표와 응답 폼

모든 투표 API는 로그인이 필요합니다.

| Method | Path | 권한 | 설명 |
|---|---|---|---|
| GET | `/api/polls` | 로그인 | 진행 중·진행 예정·종료 투표 목록 |
| GET | `/api/polls/{id}` | 로그인 | 공개된 투표 상세와 질문·선택지·질문 미디어 |
| POST | `/api/polls/{id}/submissions` | 로그인 | 투표 응답 제출 |
| GET | `/api/polls/{id}/submissions/me` | 로그인 | 내 제출 내역 |
| GET | `/api/polls/{id}/results` | 로그인 | 설정된 공개 시각 이후 결과 조회 |
| GET | `/api/admin/polls` | `ADMIN` 이상 | 전체 투표 목록 |
| GET | `/api/admin/polls/{id}` | `ADMIN` 이상 | 실시간 집계와 제출 내역 페이지 |
| POST | `/api/admin/polls` | `ADMIN` 이상 | 투표 생성 |
| PUT | `/api/admin/polls/{id}` | `ADMIN` 이상 | 응답 전 질문·선택지 전체 수정 |
| PATCH | `/api/admin/polls/{id}/settings` | `ADMIN` 이상 | 응답 후에도 가능한 설정 수정 |
| POST | `/api/admin/polls/{id}/close` | `ADMIN` 이상 | 되돌릴 수 없는 즉시 종료 |
| POST | `/api/admin/polls/{id}/options/{optionId}/image` | `ADMIN` 이상 | 선택지 이미지 등록·교체 |
| DELETE | `/api/admin/polls/{id}/options/{optionId}/image` | `ADMIN` 이상 | 선택지 이미지 삭제 |
| POST | `/api/admin/polls/{id}/questions/{questionId}/media` | `ADMIN` 이상 | 질문 이미지·동영상 업로드, 합계 최대 3개 |
| PATCH | `/api/admin/polls/{id}/questions/{questionId}/media/order` | `ADMIN` 이상 | 질문 이미지·동영상 통합 순서 변경 |
| DELETE | `/api/admin/polls/{id}/questions/{questionId}/media/{mediaId}` | `ADMIN` 이상 | 질문 미디어 삭제 |
| DELETE | `/api/admin/polls/{id}` | `ADMIN` 이상 | 투표 삭제, 응답 포함 강제 삭제는 `SUPER_ADMIN` |

질문 유형은 단일 선택, 복수 선택, 주관식 단답, 주관식 장문입니다. 질문마다 이미지·동영상을 통합 순서로 최대 3개 첨부할 수 있고, 응답이 시작되면 질문 미디어도 잠깁니다. 결과 공개 시각이 종료 전이면 사용자에게 진행 중 집계도 공개됩니다. 익명 투표 참여자 신원은 `ADMIN`에게 숨기고 `SUPER_ADMIN`에게만 제공합니다.

### 분실물

| Method | Path | 권한 | 설명 |
|---|---|---|---|
| GET | `/api/lost-items` | 공개 | 목록 조회 및 페이지네이션 |
| GET | `/api/lost-items/{id}` | 공개 | 상세 조회와 조회수 증가 |
| POST | `/api/lost-items` | `STAFF` 이상 | multipart 공지·사진 등록 |
| PATCH | `/api/lost-items/{id}` | `STAFF` 이상 | multipart 내용·사진 수정 |
| PATCH | `/api/lost-items/{id}/status` | `STAFF` 이상 | `HOLDING`/`RETURNED` 상태 변경 |
| PATCH | `/api/lost-items/{id}/pin` | `STAFF` 이상 | 상단 고정 변경 |
| DELETE | `/api/lost-items/{id}` | `STAFF` 이상 | 공지와 사진 삭제 |

목록은 `page=0`, `size=20`, `sort=NEWEST`가 기본이고 페이지 크기는 최대 100입니다. 사진은 최대 5개입니다.

### 생일축하 쪽지

| Method | Path | 권한 | 설명 |
|---|---|---|---|
| GET | `/api/birthday-messages` | 공개 | 목록 조회 (`LATEST`, `OLDEST`, `MOST_LIKED`) |
| GET | `/api/birthday-messages/{id}` | 공개 | 상세 조회 |
| GET | `/api/birthday-messages/me` | 로그인 | 내가 작성한 활성 쪽지 조회 |
| POST | `/api/birthday-messages` | 로그인 | 쪽지 작성, 사용자당 활성 1개 |
| DELETE | `/api/birthday-messages/{id}` | 작성자 | 내 쪽지 삭제 |
| PUT | `/api/birthday-messages/{id}/heart` | 로그인 | 하트 추가 |
| DELETE | `/api/birthday-messages/{id}/heart` | 로그인 | 하트 취소 |
| GET | `/api/admin/birthday-messages` | `STAFF` 이상 | 권한별 작성자 정보를 포함한 목록 |
| GET | `/api/admin/birthday-messages/{id}/hearts` | `STAFF` 이상 | 하트를 누른 사용자 목록 |
| DELETE | `/api/admin/birthday-messages/{id}` | `STAFF` 이상 | 관리자 삭제 |

공개 목록은 `page=0`, `size=30`이 기본이고 페이지 크기는 최대 100입니다. 공개 작성자 정보와 관리자에게 보이는 개인정보 범위는 조회 권한에 따라 제한됩니다.

### 동적 QR 사용자 조회

| Method | Path | 권한 | 설명 |
|---|---|---|---|
| POST | `/api/qr/search` | `STAFF` 이상 | 원본 정보로 사용자 검색 후 권한별 마스킹 결과를 기본 20명씩 페이지 반환 |
| PATCH | `/api/qr/users/{userUuid}/role` | `ADMIN` 이상 | 사용자 관리 권한 변경 |
| POST | `/api/qr/tokens` | 로그인 | 내 동적 QR 토큰 발급 |
| POST | `/api/qr/scan` | `BOOTH_MANAGER`, `STAFF`, `ADMIN`, `SUPER_ADMIN` | 권한별 사용자 정보 조회 |

QR에는 개인정보나 Access Token을 넣지 않습니다. 서버는 256비트 난수 토큰의 SHA-256 해시, `userUuid`, 만료 시각만 저장합니다. 기본 유효시간은 60초입니다.

일반 QR 사용자 조회와 부스 스탬프 처리는 별도 API입니다. 스탬프 지급은 반드시 `/api/booths/{boothId}/stamps/**`를 사용해야 담당 부스와 지급 가능 여부를 검증합니다.

사용자 관리 권한은 기존 ENUM(`USER`, `STAFF`, `ADMIN`, `SUPER_ADMIN`)으로 유지합니다. `ADMIN`은 USER·STAFF 범위만 변경하고 `SUPER_ADMIN`만 ADMIN 이상을 지정할 수 있습니다. `BOOTH_MANAGER`는 부스 담당자 관계와 별도로 관리하므로 사용자 권한 변경 API 대상이 아닙니다.

### 대나무숲 익명 채팅

모든 사용자 API는 로그인이 필요합니다. 사용자 UUID는 메시지와 신고의 내부 소유권 판정에만 사용하고 일반 응답에는 노출하지 않습니다.

| Method | Path | 권한 | 설명 |
|---|---|---|---|
| GET | `/api/bamboo` | 로그인 | 운영 상태, 내 닉네임과 현재 커서 |
| GET | `/api/bamboo/nickname/suggest` | 로그인 | 사용 가능한 임시 닉네임 제안 |
| POST | `/api/bamboo/nickname` | 로그인 | 변경 불가능한 닉네임 확정 |
| GET | `/api/bamboo/messages` | 로그인 | 과거 메시지 또는 커서 이후 변경 조회 |
| POST | `/api/bamboo/messages` | 로그인 | 메시지 작성 |
| POST | `/api/bamboo/messages/{id}/report` | 로그인 | 메시지 신고 |
| GET/PATCH/POST | `/api/admin/bamboo/**` | 기능별 상이 | STAFF 이상 신고 처리·닉네임 변경, ADMIN 이상 차단·운영 설정 |
| GET | `/api/admin/bamboo/messages/{id}/author` | `SUPER_ADMIN` | 작성자 신원 확인 및 감사 로그 |

상세 폴링 방식, 요청·응답과 오류 코드는 [대나무숲 프런트 API 문서](docs/frontend-bamboo-api.md)를 확인합니다. 운영 DB에는 [bamboo-schema.sql](docs/bamboo-schema.sql)을 먼저 적용해 `utf8mb4` 문자셋을 보장하는 것을 권장합니다.

`POST /api/admin/bamboo/messages/{id}/mute-author`는 `ADMIN` 이상만 호출할 수 있으며 `minutes`와 함께 1~200자의 `reason`이 필요합니다. 관리자 HTML 화면에서는 메시지 기반 차단 외에 익명 닉네임 검색을 통한 직접 차단·해제를 제공합니다. 두 경로 모두 처리 관리자, 대상, 처리 시각, 기간, 사유와 근거 메시지를 `bamboo_moderation_audits`에 저장합니다. 차단 감사 이력과 닉네임 직접 차단은 현재 관리자 HTML 화면에서만 제공하며 별도 REST 조회·처리 API는 없습니다.

운영 전에는 다음 조건을 반드시 확인합니다.

- `bamboo-schema.sql`을 애플리케이션보다 먼저 적용하여 다섯 테이블과 `utf8mb4` 문자셋을 보장합니다.
- 현재 변경 커서 발급기는 단일 애플리케이션 인스턴스를 전제로 합니다. 동일 DB를 사용하는 Festa 서버를 두 대 이상 동시에 실행하지 않습니다.
- 사용자 인증 캐시는 SSO 부하를 줄이기 위해 기본 60초간 유지됩니다. 로그아웃·토큰 폐기 직후에도 최대 이 시간 동안 대나무숲 요청이 통과할 수 있으므로 필요하면 `BAMBOO_IDENTITY_TTL`을 줄입니다.
- 애플리케이션의 16KiB 본문 방어는 `Content-Length`가 있는 요청을 우선 차단합니다. Chunked 요청까지 제한하려면 Nginx 등 프록시에도 `/api/bamboo` 요청 크기 제한을 설정합니다.
- 신고 메일을 사용하려면 `BAMBOO_ALERT_TO`를 실제 수신 주소로 설정합니다. 비어 있으면 신고 기록은 정상 저장되지만 메일은 발송되지 않습니다.
- 신고 누적 알림 메일 제목은 `[2026 천보축전] 오픈채팅 이용 경고`이며 `BAMBOO_ALERT_TO`에 설정한 관리자 주소로 발송됩니다. 신고된 사용자에게 자동 발송되는 경고 메일은 아닙니다.

### 프런트 이벤트와 접속 현황

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| POST | `/api/analytics/events` | 선택 | 페이지 방문·행동 이벤트 최대 20개 일괄 수집 |
| POST | `/api/presence/heartbeat` | 없음 | 익명 브라우저 세션의 route와 활동 시각 갱신 |

이벤트는 비동기 JDBC Batch로 저장하며 중복 `eventId`, 시간 범위와 세션별 rate limit을 검증합니다. Presence는 DB에 저장하지 않고 JVM 메모리에서 기본 150초 후 만료됩니다.

## 관리자 페이지

관리자 페이지는 Thymeleaf로 제공됩니다.

| Path | 권한 | 설명 |
|---|---|---|
| `/admin/login` | 공개 | 관리자 로그인 |
| `/admin` | `BOOTH_MANAGER` 이상 운영 권한 | 권한별 대시보드 |
| `/admin/qr` | `BOOTH_MANAGER`, `STAFF`, `ADMIN`, `SUPER_ADMIN` | 일반 QR 사용자 조회 |
| `/admin/stamps` | 담당 `BOOTH_MANAGER`, `ADMIN` 이상 | 스탬프 지급·회수 및 페이지 이력 |
| `/admin/booths` | `ADMIN` 이상 | 부스 지도와 미디어·담당자 관리 |
| `/admin/performances` | `ADMIN` 이상 | 공연팀 관리 |
| `/admin/polls` | `ADMIN` 이상 | 투표·응답 폼 생성과 실시간 현황 |
| `/admin/lost-items` | `STAFF` 이상 | 분실물 관리 |
| `/admin/birthday-messages` | `STAFF` 이상 | 생일축하 쪽지·하트 사용자 관리 |
| `/admin/bamboo` | `STAFF` 이상 | 실시간·신고 채팅 조회, ADMIN 이상 익명 참여자 차단·감사 이력 관리 |
| `/admin/system` | `SUPER_ADMIN` | 실시간 시스템 모니터링 |

관리자 부스 담당자와 사용자 검색은 축제 서비스에 연결된 사용자만 대상으로 합니다. 일반 사용자 조회와 스탬프 임의 지급용 검색은 20명 단위 숫자 페이지를 사용합니다. 스탬프 페이지에서 `BOOTH_MANAGER`는 담당 부스만, `ADMIN` 이상은 모든 스탬프 지급 부스를 볼 수 있습니다.

대나무숲 운영 화면은 `실시간 채팅`을 기본으로 열고 3초마다 변경 커서를 확인해 새 글이나 운영 조치가 있을 때만 목록을 갱신합니다. 신고된 채팅은 별도 탭에서 확인합니다. STAFF는 메시지 조회·숨김·삭제·복구와 닉네임 변경까지만 가능하고, 익명 참여자 검색과 작성 차단·해제는 `ADMIN` 이상만 가능합니다. 차단과 해제에는 사유가 필수이며 처리자·처리 시각·대상·기간과 함께 DB 감사 이력에 저장됩니다. 실제 사용자 신원은 노출되지 않으며 메시지 작성자 신원 조회는 기존처럼 `SUPER_ADMIN`에게만 허용됩니다. 대나무숲 열기·읽기 전용·자동 종료 설정은 `ADMIN` 이상만 변경할 수 있습니다.

## 미디어와 트랜잭션

- R2 기본 제한은 이미지 10MB, 동영상 200MB입니다.
- 전체 multipart 요청 기본 제한은 650MB입니다.
- 보호된 multipart API는 본문 파싱 전에 인증과 권한을 먼저 검사합니다.
- 업로드한 파일은 DB 트랜잭션이 롤백되면 정리합니다.
- 기존 파일 삭제는 DB 커밋 이후 실행하여 DB가 롤백됐는데 파일만 사라지는 상황을 방지합니다.
- 부스 미디어 업로드·삭제·정렬은 부스 행 잠금으로 동시 변경을 직렬화합니다.
- 기본 R2 prefix는 `festa2026_performance`, `festa2026_lost_items`, `festa2026_booths`, `festa2026_polls`이며 환경변수로 변경할 수 있습니다.

## 공통 오류 응답

API 오류는 다음 형태로 반환합니다.

```json
{
  "code": "INVALID_REQUEST",
  "message": "요청 정보를 확인해 주세요.",
  "timestamp": "2026-08-17T00:00:00Z"
}
```

주요 공통 매핑:

| HTTP | code | 상황 |
|---|---|---|
| 400 | `INVALID_REQUEST` | 검증 실패 또는 잘못된 JSON |
| 400 | `INVALID_PARAMETER` | 잘못된 enum·쿼리 파라미터 |
| 400 | `INVALID_MULTIPART_REQUEST` | 잘못된 multipart 형식 |
| 401 | `UNAUTHORIZED` | 인증 필요 또는 만료 |
| 403 | 도메인별 `*_FORBIDDEN` | 권한 부족 |
| 405 | `METHOD_NOT_ALLOWED` | 지원하지 않는 HTTP 메서드 |
| 413 | `UPLOAD_TOO_LARGE` | 업로드 크기 초과 |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | 지원하지 않는 Content-Type |
| 500 | `INTERNAL_ERROR` | 예상하지 못한 서버 오류 |

예상하지 못한 오류의 상세 내용은 응답에 노출하지 않고 `requestId`와 함께 서버 로그에 기록합니다.

## DB 관리

별도 Flyway/Liquibase 마이그레이션은 사용하지 않으며 `spring.jpa.hibernate.ddl-auto=update`로 엔티티 스키마를 반영합니다. 개발 중 스키마를 초기화할 때는 DB를 삭제하고 애플리케이션을 다시 실행할 수 있지만, 운영 데이터가 있는 환경에서는 자동 변경 전에 반드시 백업과 스키마 검토가 필요합니다.

기존 대나무숲 DB에 이번 변경을 배포하면 `bamboo_moderation_audits`가 새로 필요합니다. 현재 설정에서는 애플리케이션 재시작 시 Hibernate가 자동 생성합니다. 운영에서 DDL을 수동 관리한다면 [bamboo-schema.sql](docs/bamboo-schema.sql)의 `bamboo_moderation_audits` 구문만 먼저 적용한 뒤 애플리케이션을 시작합니다.

주요 테이블:

```text
festival_users
school_verification_requests
festival_booths
festival_booth_managers
festival_booth_media
festival_booth_favorites
festival_booth_stamps
festival_stamp_events
festival_qr_tokens
festival_performances
festival_performance_members
festival_performance_links
festival_performance_media
lost_item_notices
lost_item_notice_images
birthday_messages
birthday_message_hearts
bamboo_messages
bamboo_nicknames
bamboo_reports
bamboo_moderation_audits
bamboo_settings
api_request_logs
frontend_event_logs
```

## 로깅과 모니터링

`/api/**`, `/admin`, `/admin/**` 요청은 `api_request_logs`에 비동기로 기록합니다. 정적 파일, Swagger와 `OPTIONS`는 제외합니다.

- 관리자·쓰기·오류·1초 이상 요청은 전부 기록합니다.
- 정상 `GET /api/**`는 기본 25% 표본 기록합니다.
- 비밀번호, 인증번호, Authorization, Cookie, 토큰, 본문과 쿼리 문자열은 저장하지 않습니다.
- `X-Request-ID`를 응답하고 같은 값을 SSO `X-Correlation-ID`로 전달합니다.
- 요청 로그와 프런트 이벤트 로그는 기본 60일 후 작은 배치로 삭제합니다.
- 프록시 헤더 신뢰는 기본 비활성화입니다.

Nginx 뒤에서 실제 클라이언트 IP를 기록하려면 외부가 보낸 전달 헤더를 제거하고 프록시가 다시 설정한 뒤 `API_REQUEST_LOG_TRUST_FORWARDED_HEADERS=true`를 사용해야 합니다.
서버는 `server.forward-headers-strategy=native`로 `X-Forwarded-Proto`/`X-Forwarded-Host`를 반영하므로, 아래처럼 Nginx가 외부 헤더를 덮어써야 리다이렉트 URL이 HTTPS로 생성됩니다.

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

학교 SSO 콜백 후 복귀 주소는 운영에서 `SYU_SSO_RETURN_URL=https://festa.syu-likelion.org/temporary-auth`처럼 절대 HTTPS URL로 설정합니다. `/temporary-auth`는 프런트엔드가 callback 결과 쿼리를 처리하는 경로이며, 백엔드는 해당 UI를 제공하지 않습니다.

Actuator는 기본적으로 `127.0.0.1:9091`에서 `health`, `prometheus`만 노출합니다. `/admin/system`의 최근 5분 그래프는 해당 브라우저 메모리에만 유지되며, 다중 인스턴스 통합 모니터링은 외부 Prometheus/Grafana 구성이 필요합니다.

## Swagger / OpenAPI

- Swagger UI: `http://localhost:8888/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8888/v3/api-docs`
- OpenAPI YAML: `http://localhost:8888/v3/api-docs.yaml`

Swagger UI의 **Authorize**에는 SSO Access Token 원문만 입력합니다. `Bearer ` 접두사는 Swagger UI가 추가합니다.

## 테스트

```powershell
.\gradlew.bat test
```

테스트는 `src/test/resources/application.properties`에서 H2 인메모리 DB와 로컬 실패용 SSO/R2 주소를 강제합니다. 개발·운영 DB나 실제 SSO/R2에 연결하지 않습니다.

주요 검증 범위:

- SSO 인증, 토큰 rotation과 권한별 개인정보 마스킹
- 학교 SSO 학생 인증, 학적정보 불일치 처리와 인증 상태 노출
- 부스·찜·통합 미디어 정렬과 대표 미디어
- 스탬프 중복 방지, 담당 부스 권한과 감사 이력 페이지네이션
- 공연·분실물·생일축하 쪽지의 권한과 제한
- 투표 중복 참여 정책, 필수 응답, 익명 신원 마스킹과 강제 종료·삭제
- 인증 전 multipart 차단과 미디어 커밋·롤백 정리
- CORS 허용/차단 Origin 및 공통 예외 응답
- API 요청 로그, 프런트 이벤트와 heartbeat

## 프런트 연동 문서

- [프런트 API 문서 목차](docs/README.md)
- [공통 API 규약](docs/frontend-api-common.md)
- [인증·회원가입](docs/frontend-auth-api.md)
- [프런트엔드 학교 SSO 연동](docs/frontend-school-sso.md)
- [계정 복구](docs/frontend-account-recovery-api.md)
- [내 정보·계정](docs/frontend-user-api.md)
- [학생 인증 기능](docs/student-verification.md)
- [비밀번호 변경](docs/frontend-password-change-api.md)
- [부스 지도](docs/frontend-booths-api.md)
- [스탬프](docs/frontend-stamps-api.md)
- [공연](docs/frontend-performances-api.md)
- [투표·응답 폼](docs/frontend-polls-api.md)
- [동적 QR](docs/frontend-qr-api.md)
- [분실물](docs/frontend-lost-items-api.md)
- [생일축하 쪽지](docs/frontend-birthday-messages-api.md)
- [프런트 이벤트](docs/frontend-analytics-api.md)
- [접속 현황 heartbeat](docs/frontend-presence-api.md)
