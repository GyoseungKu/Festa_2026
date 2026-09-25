# 관리자 웹 경로와 인증

[문서 목차](README.md) · [프런트 연동 시작하기](frontend-getting-started.md)

`/admin/**`는 Thymeleaf HTML 화면과 폼 처리 경로입니다. React의 `/api/**`와 같은 기능이라도 인증 방식·메서드·응답이 다릅니다. 기능별 권한은 [프로젝트 관리자 페이지 표](../README.md)와 각 도메인 문서를 따릅니다.

## 로그인·CSRF·오류

역할별 허용·차단과 학생 인증·담당 부스 등 추가 조건은 [권한별 API·관리자 기능 허용표](api-role-permissions.md)를 확인합니다.

- `GET /admin/login`으로 로그인 폼을 연 뒤 `POST /admin/login`에 `loginId`, `password`와 폼의 CSRF 값을 전송합니다. 성공 시 `/admin`으로 리다이렉트하고 실패 시 오류가 포함된 HTML 폼을 반환할 수 있습니다.
- 관리자 쿠키 기본 이름은 `festivalAdminAccess`, `festivalAdminRefresh`이며 HttpOnly, Path=`/admin`입니다. 일반 API의 `festivalRefreshToken`과 별개입니다. Swagger Bearer 인증만으로 관리자 웹 요청을 인증할 수 없습니다.
- 변경 폼은 CSRF 보호 대상입니다. 기존 Thymeleaf 폼의 `_csrf` hidden 필드를 유지합니다. 일반 `/api/**`는 이 CSRF 검사에서 제외됩니다. Spring CSRF 필터에서 발생한 403은 도메인 JSON 오류 형태를 보장하지 않습니다.
- 성공한 폼은 대체로 리다이렉트하며 오류도 HTML이나 flash 메시지로 처리합니다. JSON `apiFetch` 래퍼로 폼 응답을 파싱하지 않습니다.
- 관리자 로그아웃은 `POST /admin/logout`입니다. GET 링크로 대체하지 않습니다. 쿠키·SSO 로그아웃 처리 후 로그인 페이지로 이동합니다.
- 관리자 폼의 `datetime-local` 입력은 화면의 한국 시각으로 처리하며, REST의 UTC Instant 문자열과 구분합니다.

## Swagger 문서 접근

`/admin/swagger-ui.html`을 열면 미로그인 상태에서는 기존 로그인 폼인 `/admin/login?next=swagger`로 이동합니다. 축제 권한 `ADMIN`, `SUPER_ADMIN`으로 로그인하면 문서로 돌아옵니다. `USER`, `BOOTH_MANAGER`, `STAFF`만 보유한 사용자는 Swagger에 접근할 수 없습니다. 일반 관리자 로그인(`/admin/login`)과 관리자 업무 화면의 기존 권한 제한은 유지합니다. `next`는 `swagger`만 특별 처리하며 임의 URL로 이동하지 않습니다.

허용 권한은 [AdminAccessService](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminAccessService.java)의 `SWAGGER_ALLOWED_ROLES`에서 관리합니다. `USER`, `BOOTH_MANAGER`, `STAFF` 항목은 주석 처리되어 있습니다. 로그인 화면·인증·쿠키 발급은 기존 관리자 로그인 하나로 통합되어 있으며 `next=swagger`는 로그인 후 이동할 목적지만 지정합니다. Swagger 요청마다 별도 허용 목록을 검사하므로 STAFF·BOOTH_MANAGER는 관리자 로그인에 성공해도 Swagger에서 403을 받습니다. Swagger 권한 부족 때문에 유효한 관리자 로그인 쿠키를 삭제하지 않습니다.

JSON은 `/admin/v3/api-docs`, YAML은 `/admin/v3/api-docs.yaml`, UI 설정은 `/admin/v3/api-docs/swagger-config`입니다. `GET /admin/v3/api-docs`만 로그인 없이 공개하여 프론트 타입 생성에 사용합니다. 나머지 화면·정적 리소스·YAML·UI 설정은 요청마다 SSO 인증과 Swagger 허용 권한을 확인합니다. 모든 문서 응답에 `Cache-Control: no-store`를 적용합니다. 미로그인 UI는 `/admin/login?next=swagger`로 이동하고, 공개 JSON 이외의 미인증 명세 요청은 401, 권한 부족은 403입니다. SSO 토큰 폐기 시 관리자 쿠키도 정리합니다.

기존 루트 `/swagger-ui.html`과 `/swagger-ui/index.html` 경로는 제공하지 않습니다. 이전 명세(`/v3/api-docs` 등)와 이전 정적 리소스 경로는 404입니다. Swagger UI 열람에는 관리자 쿠키가 필요하고 Swagger의 `Authorize`에는 실제 `/api/**` 호출에 사용할 Bearer Token을 별도로 입력합니다.

## 웹 화면 전용 JSON

| 메서드·경로 | 인증·권한 | 응답 |
|---|---|---|
| `GET /admin/bamboo/cursor` | 관리자 쿠키, STAFF 이상 | 200 `{ "cursor": 105 }`; 인증·권한 확인 실패는 본문 없는 401 |
| `GET /admin/system/snapshot` | 관리자 쿠키, SUPER_ADMIN | 200 `SystemSnapshot`; 인증 후 권한 부족은 403 `SUPER_ADMIN_REQUIRED` |

스냅샷의 전체 필드는 [SystemMonitoringService](../src/main/java/org/syu_likelion/Festa_2026/monitoring/SystemMonitoringService.java)의 `SystemSnapshot`과 Swagger 스키마를 기준으로 합니다. 접속 추정치는 사용자 수가 아닌 최근 heartbeat 세션 수이고 서버 지표는 현재 인스턴스 기준입니다. `/api/**`용 CORS 설정을 관리자 웹 경로에 적용한다고 가정하지 않습니다.

학생회비 명단 관리, 대나무숲 닉네임 참여자 검색·직접 차단·감사 이력은 웹 전용 기능입니다. 해당 기능에 별도 REST API가 있다고 가정하지 않습니다. 변경 폼의 정확한 파라미터는 아래 연결된 컨트롤러와 템플릿을 기준으로 합니다.

## 전체 관리자 경로

### 전체 회원 조회

사용자 조회(`/admin/qr`)의 **전체 회원 조회·필터** 버튼에서 `/admin/qr/users`로 이동합니다. 전체 목록과 모든 필터는 ADMIN·SUPER_ADMIN만 이용할 수 있습니다. STAFF에게는 버튼을 표시하지 않으며 직접 URL로 요청해도 403으로 거부합니다. STAFF의 기존 검색 조회와 개인정보 마스킹은 유지합니다. BOOTH_MANAGER는 기존 QR 조회만 가능합니다.

| 쿼리 | 기본값 | 의미 |
|---|---|---|
| `page` | `0` | 0부터 시작하는 페이지. 음수는 0, 최대 1,000,000으로 보정 |
| `size` | `50` | 1–100으로 보정. 화면에서는 20·50·100명 선택 |
| `role` | 생략 | ADMIN 이상 전용. USER·STAFF·ADMIN·SUPER_ADMIN은 관리 권한, BOOTH_MANAGER는 겸임 여부 |
| `verified` | 생략 | true: 학생 인증 완료, false: 미인증·회수. 생략하면 전체 |
| `paid` | 생략 | true: 학생 인증 완료 및 납부 확인, false: 미납부·미인증. 생략하면 전체 |

필터는 AND 조건이며 페이지 이동 시 유지합니다. 조건을 바꿔 조회하면 첫 페이지부터 표시합니다. 범위를 벗어난 페이지는 빈 목록이며 ‘처음’으로 돌아갈 수 있습니다. 전체 인원은 현재 필터에 맞는 **축제 회원 행 수**이고 SSO 전체 가입자 수가 아닙니다. SSO 탈퇴자도 축제 연결이 남아 있다면 포함되며 정상 프로필 응답에 해당 계정이 없으면 ‘알 수 없음’으로 표시합니다. SSO 장애는 빈 목록으로 숨기지 않고 조회 오류를 안내합니다.

DB에서 필터·정렬(id 내림차순)·페이지 제한을 먼저 적용하고 해당 페이지의 UUID만 최대 100개 일괄 SSO 조회합니다. 팔찌 상태도 현재 페이지에 한해 일괄 조회하며 재가입 식별값 대조를 유지합니다. SSO에만 저장된 이름·학과 검색은 기존 사용자 검색을 이용합니다. 기존 검색은 전체 연결 프로필을 대조하므로 전체 목록의 DB 페이지 조회와 성능 특성이 다릅니다. 전체 목록은 HTML 전용이고 별도 공개 REST API·다운로드 기능은 제공하지 않습니다. 응답은 `Cache-Control: no-store`입니다.

| 메서드 | 경로 | 구현 |
|---|---|---|
| GET | `/admin/qr/users` | [AdminUserDirectoryController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminUserDirectoryController.java) |

### 입장 팔찌 (2026-09-16 추가)

관리자 쿠키와 POST의 CSRF 토큰이 필요합니다. [정책·저장·본인 API](frontend-wristbands-api.md)를 함께 확인합니다.

| 메서드 | 경로 | 권한·용도 |
|---|---|---|
| GET | `/admin/wristbands` | STAFF 이상 조회·지급 화면 |
| POST | `/admin/wristbands/scan` | STAFF 이상 QR 조회 |
| POST | `/admin/wristbands/search` | STAFF 이상 사용자 검색 |
| POST | `/admin/wristbands/issue` | STAFF 이상 지급, 학생 인증 필수 |
| GET | `/admin/wristbands/manage` | ADMIN 이상 전체 목록·현재 인원 |
| GET | `/admin/wristbands/records/{id}` | ADMIN 이상 상세·이력 |
| POST | `/admin/wristbands/records/{id}/revoke` | ADMIN 이상 철회, 사유·조회 버전 필수 |

### 기존 기능

2026-09-17 컨트롤러 선언과 대조했습니다. 아래에는 두 JSON 조회도 포함됩니다. 같은 경로의 GET과 POST는 서로 다른 작업입니다.

타임테이블 폼은 ADMIN 이상이며 `title`, `performanceId`, `startsAt`, `endsAt`, `publishedAt`을 보냅니다. 세 시각은 `yyyy-MM-dd'T'HH:mm` 형식의 한국 시간입니다. JSON REST의 시간대 포함 Instant와 구분합니다. 실제 신원으로 대나무숲 참여자를 찾는 `/admin/bamboo/users/**`는 SUPER_ADMIN 전용이며 일반 닉네임 검색과 다릅니다.

| 메서드 | 경로 | 구현 |
|---|---|---|
| GET | `/admin` | [AdminPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminPageController.java) |
| GET | `/admin/bamboo` | [AdminBambooPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminBambooPageController.java) |
| GET | `/admin/bamboo/cursor` | [AdminBambooPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminBambooPageController.java) |
| GET | `/admin/bamboo/users` | [AdminBambooIdentityController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminBambooIdentityController.java) |
| POST | `/admin/bamboo/users/search` | [AdminBambooIdentityController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminBambooIdentityController.java) |
| POST | `/admin/bamboo/users/{userUuid}/mute` | [AdminBambooIdentityController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminBambooIdentityController.java) |
| POST | `/admin/bamboo/messages/{id}/author` | [AdminBambooPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminBambooPageController.java) |
| POST | `/admin/bamboo/messages/{id}/mute` | [AdminBambooPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminBambooPageController.java) |
| POST | `/admin/bamboo/messages/{id}/nickname` | [AdminBambooPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminBambooPageController.java) |
| POST | `/admin/bamboo/messages/{id}/status` | [AdminBambooPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminBambooPageController.java) |
| POST | `/admin/bamboo/participants/mute` | [AdminBambooPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminBambooPageController.java) |
| POST | `/admin/bamboo/settings` | [AdminBambooPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminBambooPageController.java) |
| GET | `/admin/birthday-messages` | [AdminBirthdayMessagePageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminBirthdayMessagePageController.java) |
| GET | `/admin/birthday-messages/{id}` | [AdminBirthdayMessagePageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminBirthdayMessagePageController.java) |
| POST | `/admin/birthday-messages/{id}/delete` | [AdminBirthdayMessagePageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminBirthdayMessagePageController.java) |
| GET | `/admin/birthday-messages/{id}/hearts` | [AdminBirthdayMessagePageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminBirthdayMessagePageController.java) |
| GET | `/admin/booths` | [AdminBoothPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminBoothPageController.java) |
| POST | `/admin/booths` | [AdminBoothPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminBoothPageController.java) |
| GET | `/admin/booths/new` | [AdminBoothPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminBoothPageController.java) |
| POST | `/admin/booths/{id}` | [AdminBoothPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminBoothPageController.java) |
| POST | `/admin/booths/{id}/delete` | [AdminBoothPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminBoothPageController.java) |
| GET | `/admin/booths/{id}/edit` | [AdminBoothPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminBoothPageController.java) |
| GET | `/admin/login` | [AdminPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminPageController.java) |
| POST | `/admin/login` | [AdminPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminPageController.java) |
| POST | `/admin/logout` | [AdminPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminPageController.java) |
| GET | `/admin/lost-items` | [AdminLostItemPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminLostItemPageController.java) |
| POST | `/admin/lost-items` | [AdminLostItemPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminLostItemPageController.java) |
| GET | `/admin/lost-items/new` | [AdminLostItemPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminLostItemPageController.java) |
| POST | `/admin/lost-items/{id}` | [AdminLostItemPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminLostItemPageController.java) |
| POST | `/admin/lost-items/{id}/delete` | [AdminLostItemPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminLostItemPageController.java) |
| GET | `/admin/lost-items/{id}/edit` | [AdminLostItemPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminLostItemPageController.java) |
| POST | `/admin/lost-items/{id}/pin` | [AdminLostItemPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminLostItemPageController.java) |
| POST | `/admin/lost-items/{id}/status` | [AdminLostItemPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminLostItemPageController.java) |
| GET | `/admin/notices` | [AdminNoticePageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminNoticePageController.java) |
| POST | `/admin/notices` | [AdminNoticePageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminNoticePageController.java) |
| GET | `/admin/notices/new` | [AdminNoticePageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminNoticePageController.java) |
| POST | `/admin/notices/{id}` | [AdminNoticePageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminNoticePageController.java) |
| POST | `/admin/notices/{id}/delete` | [AdminNoticePageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminNoticePageController.java) |
| GET | `/admin/notices/{id}/edit` | [AdminNoticePageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminNoticePageController.java) |
| POST | `/admin/notices/{id}/pin` | [AdminNoticePageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminNoticePageController.java) |
| GET | `/admin/performances` | [AdminPerformancePageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminPerformancePageController.java) |
| POST | `/admin/performances` | [AdminPerformancePageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminPerformancePageController.java) |
| GET | `/admin/performances/new` | [AdminPerformancePageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminPerformancePageController.java) |
| POST | `/admin/performances/{id}` | [AdminPerformancePageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminPerformancePageController.java) |
| POST | `/admin/performances/{id}/delete` | [AdminPerformancePageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminPerformancePageController.java) |
| GET | `/admin/performances/{id}/edit` | [AdminPerformancePageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminPerformancePageController.java) |
| GET | `/admin/polls` | [AdminPollPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminPollPageController.java) |
| POST | `/admin/polls` | [AdminPollPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminPollPageController.java) |
| GET | `/admin/polls/new` | [AdminPollPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminPollPageController.java) |
| GET | `/admin/polls/{id}` | [AdminPollPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminPollPageController.java) |
| POST | `/admin/polls/{id}` | [AdminPollPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminPollPageController.java) |
| POST | `/admin/polls/{id}/close` | [AdminPollPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminPollPageController.java) |
| POST | `/admin/polls/{id}/delete` | [AdminPollPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminPollPageController.java) |
| GET | `/admin/polls/{id}/edit` | [AdminPollPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminPollPageController.java) |
| GET | `/admin/qr` | [AdminPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminPageController.java) |
| POST | `/admin/qr/scan` | [AdminPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminPageController.java) |
| POST | `/admin/qr/search` | [AdminPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminPageController.java) |
| POST | `/admin/qr/users/{userUuid}/role` | [AdminPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminPageController.java) |
| POST | `/admin/qr/users/{userUuid}/school-verification` | [AdminPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminPageController.java) |
| GET | `/admin/school-verifications` | [AdminSchoolVerificationPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminSchoolVerificationPageController.java) |
| POST | `/admin/school-verifications/{id}/approve` | [AdminSchoolVerificationPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminSchoolVerificationPageController.java) |
| POST | `/admin/school-verifications/{id}/delete` | [AdminSchoolVerificationPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminSchoolVerificationPageController.java) |
| GET | `/admin/sponsors` | [AdminSponsorPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminSponsorPageController.java) |
| POST | `/admin/sponsors` | [AdminSponsorPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminSponsorPageController.java) |
| GET | `/admin/sponsors/new` | [AdminSponsorPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminSponsorPageController.java) |
| POST | `/admin/sponsors/{id}` | [AdminSponsorPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminSponsorPageController.java) |
| POST | `/admin/sponsors/{id}/delete` | [AdminSponsorPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminSponsorPageController.java) |
| GET | `/admin/sponsors/{id}/edit` | [AdminSponsorPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminSponsorPageController.java) |
| GET | `/admin/stamps` | [AdminStampPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminStampPageController.java) |
| POST | `/admin/stamps/qr/action` | [AdminStampPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminStampPageController.java) |
| POST | `/admin/stamps/qr/lookup` | [AdminStampPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminStampPageController.java) |
| POST | `/admin/stamps/user/action` | [AdminStampPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminStampPageController.java) |
| GET | `/admin/student-fees` | [AdminStudentFeeController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminStudentFeeController.java) |
| POST | `/admin/student-fees` | [AdminStudentFeeController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminStudentFeeController.java) |
| POST | `/admin/student-fees/delete` | [AdminStudentFeeController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminStudentFeeController.java) |
| GET | `/admin/system` | [AdminSystemPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminSystemPageController.java) |
| GET | `/admin/system/snapshot` | [AdminSystemPageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminSystemPageController.java) |
| GET | `/admin/timetable` | [AdminTimetablePageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminTimetablePageController.java) |
| GET | `/admin/timetable/new` | [AdminTimetablePageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminTimetablePageController.java) |
| POST | `/admin/timetable` | [AdminTimetablePageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminTimetablePageController.java) |
| GET | `/admin/timetable/{id}/edit` | [AdminTimetablePageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminTimetablePageController.java) |
| POST | `/admin/timetable/{id}` | [AdminTimetablePageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminTimetablePageController.java) |
| POST | `/admin/timetable/{id}/delete` | [AdminTimetablePageController](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminTimetablePageController.java) |

## React와 서버 경로 분리

서버 페이지의 CSS·JS·이미지는 `/admin/assets/**`, QR 라이브러리는 `/admin/assets/webjars/jsqr/1.4.0/dist/jsQR.js`에서 제공합니다. 약관 페이지도 이 리소스를 사용합니다. 로그인 화면·약관에 필요한 정적 리소스는 공개이며 Swagger UI 리소스(`/admin/swagger-ui/**`)에는 기존 인증을 유지합니다. 모든 WebJar를 공개하지 않습니다.

루트 `/css/**`, `/js/**`, `/images/**`, `/webjars/**`, `/favicon.ico`와 이전 Swagger 별칭은 백엔드가 제공하지 않습니다. 메일 배너는 기존 클래스패스 파일을 CID 첨부하므로 HTTP 경로 변경의 영향을 받지 않습니다.

Nginx는 아래 경로만 백엔드로 전달하고 나머지는 React 빌드에서 제공합니다. 아래 예시는 같은 도메인·루트 배포 기준이며 `root`는 실제 프런트 빌드 경로로 변경합니다. 백엔드 `proxy_pass` 뒤에 URI를 붙이지 않아 원래 경로를 보존합니다. 다른 정규식 location을 추가할 때는 우선순위를 확인합니다.

```nginx
root /srv/Make-A-Wish_FE/dist;
index index.html;

location ~ ^/(?:api(?:/|$)|admin(?:/|$)|terms(?:/|$)|auth/sso/callback$) {
    proxy_pass http://127.0.0.1:8888;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $remote_addr;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-Host $host;
    proxy_set_header Forwarded "";
}

location /assets/ {
    try_files $uri =404;
}

location / {
    try_files $uri $uri/ /index.html;
}
```

`/auth/sso/callback`은 백엔드가 처리하고 `/auth/school/result`는 React가 처리합니다. `/auth/**` 전체를 백엔드로 보내지 않습니다. 기존 프록시의 `/images`, `/css`, `/js`, `/webjars`, `/favicon.ico`, 이전 Swagger 별칭 분기는 제거합니다. `/actuator/**`는 이 공개 서버 분기에 포함하지 않습니다.

Nginx 동작 기준: [location](https://nginx.org/en/docs/http/ngx_http_core_module.html#location), [proxy_pass](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_pass).

## 스탬프 완성 상품 지급

`/admin/stamps`의 **상품 지급 관리**에서 `/admin/stamps/prizes`로 이동합니다. ADMIN·SUPER_ADMIN만 사용자 QR로 스탬프판을 조회하고, 6개 이상 보유한 사용자에게 상품 지급 완료를 기록할 수 있습니다. 스탬프판은 1회 유지하며 상품 수령 후 초기화하지 않습니다. 사용자별 중복 지급은 차단하고 지급 시각·처리 관리자·지급 당시 스탬프 수를 남깁니다.

화면 경로, QR 유효기간, 오류와 배포 스키마는 [스탬프 문서](frontend-stamps-api.md#관리자-상품-지급-확인)를 참고합니다.
