# 권한별 API·관리자 기능 허용표

[문서 목차](README.md) · [API 전체 색인](api-endpoint-index.md) · [관리자 웹 경로](admin-web-api.md)

기준일: **2026-09-18**. 컨트롤러 및 서비스의 실제 권한 검사 기준입니다. 아래 표는 역할에 따른 접근 가능 여부이며 입력 검증·계정 상태·학생 인증·운영 시간·대상 소유권을 통과해야 요청이 성공합니다.

- [표 읽는 방법](#1-표-읽는-방법)
- [역할별 기능 요약·예외](#2-역할별-기능-요약)
- [조건 코드](#3-api-상세표의-조건-코드)
- [관리자 웹 전용 기능](#4-관리자-웹-전용-기능)
- [개인정보 표시·오류 처리](#5-개인정보-표시와-오류-처리)
- [근거 코드](#6-근거-코드와-유지보수)
- [전체 API 허용표](#7-메서드경로별-전체-api-허용표)

## 1. 표 읽는 방법

- `O`: 해당 역할로 접근 가능. 일반적인 업무 검증은 별도로 적용됩니다.
- `X`: 해당 역할만으로는 접근 불가. 권한이 추가되거나 변경되지 않는 한 사용할 수 없습니다.
- `C`: 조건부 접근. 각 표의 조건 코드와 아래 예외 설명을 확인합니다.
- `비로그인`(상세표의 `anonymous`): 유효한 Access Token이 없는 상태. Refresh 쿠키나 학교 인증 세션이 필요한 흐름은 별도로 표시합니다.
- `B` = BOOTH_MANAGER, `S` = STAFF, `A` = ADMIN, `SA` = SUPER_ADMIN입니다. 일반 회원은 USER이며 MEMBER라는 enum은 없습니다.

권한은 SSO 공통 역할이 아닌 **Festa의 festivalRoles** 기준입니다. BOOTH_MANAGER는 USER·STAFF·ADMIN 등 관리 권한과 겸임할 수 있습니다. 표의 B·S 열은 각각의 단독 권한 기준입니다. 예를 들어 STAFF + BOOTH_MANAGER는 담당 부스 스탬프를 처리할 수 있지만 STAFF 단독은 불가합니다. 프런트는 enum 순서나 `role >= STAFF` 같은 비교로 판단하지 않습니다.

`/api/**` 보호 API는 Bearer 인증, `/admin/**`는 관리자 쿠키를 사용합니다. 관리자 POST 폼은 CSRF 토큰도 필요합니다. Swagger에 로그인한 사실만으로 Bearer 인증이 완료되지는 않습니다.

## 2. 역할별 기능 요약

| 기능 | USER | B | S | A | SA |
|---|---|---|---|---|---|
| 본인 정보·QR·찜·스탬프판·팔찌 상태 | O | O | O | O | O |
| 대나무숲·생일축하·투표 사용자 API | C | C | C | C | C |
| 관리자 홈·일반 QR 스캔 | X | O | O | O | O |
| 이름·학번 등 사용자 검색 | X | X | O | O | O |
| 전체 회원 목록·필터 | X | X | X | O | O |
| 학생 인증·납부 여부 조회(타인) | X | X | O | O | O |
| 관리 권한 변경 | X | X | X | C | C |
| 사용자 학생 인증 직접 부여·회수 | X | X | X | O | O |
| 학적정보 불일치 요청 목록·승인·삭제 | X | X | X | X | O |
| 학생회비 명부 관리 | X | X | X | O | O |
| 입장 팔찌 지급 | X | X | C | C | C |
| 팔찌 전체 이력·지급 철회 | X | X | X | C | C |
| 스탬프 QR 조회·지급·회수·이력 | X | C | X | O | O |
| 사용자 UUID로 스탬프 지급·회수 | X | X | X | O | O |
| 부스 정보·공연·타임테이블·협찬사 관리 | X | X | X | O | O |
| 일반 공지·분실물·생일 쪽지 관리 | X | X | O | O | O |
| 대나무숲 메시지 상태·닉네임 변경 | X | X | O | O | O |
| 대나무숲 메시지 작성자 차단 | X | X | C | O | O |
| 대나무숲 차단 해제·운영 설정 변경 | X | X | X | O | O |
| 대나무숲 실제 작성자 신원 확인 | X | X | X | X | O |
| 투표 관리·제출 내역 조회 | X | X | X | C | O |
| 응답 있는 투표 강제 삭제 | X | X | X | X | C |
| 시스템 모니터링 | X | X | X | X | O |
| Swagger UI·명세·관련 리소스 | X | X | X | O | O |

### 추가 조건과 중요한 예외

1. **학생 인증:** 대나무숲·생일축하·투표의 사용자 API는 조회부터 학생 인증이 필수입니다. ADMIN·SUPER_ADMIN도 사용자 API를 호출할 때는 동일합니다. 학생회비 납부는 이 세 기능의 이용 조건이 아닙니다. 각 관리자 운영 API에는 같은 학생 인증 조건을 일괄 적용하지 않습니다.
2. **본인 대상:** `/api/users/me/**`는 로그인한 본인 대상입니다. 관리자라고 다른 사용자의 비밀번호·프로필을 이 경로로 변경할 수 없습니다. 생일 쪽지 사용자 DELETE는 본인 글만 삭제하며 타인 글 삭제는 별도 관리자 API를 사용합니다.
3. **전체 회원:** `GET /admin/qr/users`는 ADMIN 이상만 가능합니다. STAFF는 기존 `/api/qr/search`, `/admin/qr/search` 검색만 사용합니다. BOOTH_MANAGER 단독은 사용자 검색도 불가하고 QR 조회만 가능합니다. 전체 목록은 축제 연결 회원을 최대 100명씩 조회합니다.
4. **권한 변경:** ADMIN은 자기 자신이나 ADMIN·SUPER_ADMIN을 변경할 수 없고, 다른 USER·STAFF를 USER/STAFF로 변경할 수 있습니다. SUPER_ADMIN은 상위 권한도 변경할 수 있지만 마지막 SUPER_ADMIN의 권한 제거는 제한됩니다. BOOTH_MANAGER는 이 API로 지정하지 않고 부스 담당자 연결로 관리합니다.
5. **학생 인증 두 경로:** `/api/qr/users/{userUuid}/school-verification`은 ADMIN 이상 직접 보정입니다. `/api/admin/school-verifications/**`는 SUPER_ADMIN 전용 불일치 요청 심사입니다. 서로 다른 기능입니다.
6. **팔찌:** 지급 대상 학생 인증 필수, 납부 여부는 안내용입니다. 이미 지급된 학생은 중복 지급하지 않으며 재가입 식별값도 확인합니다. 철회는 ADMIN 이상이며 사유·조회 버전이 필요합니다. 지급·철회는 관리자 웹 전용입니다.
7. **스탬프:** BOOTH_MANAGER는 배정된 부스만, ADMIN 이상은 모든 대상 부스를 처리합니다. 부스의 스탬프 설정·대상 상태·중복 지급 등 검증은 별개입니다. STAFF 단독에는 처리 권한이 없습니다.
8. **대나무숲:** STAFF는 메시지 작성자 차단에서 `minutes > 0`만 가능합니다. `minutes=0` 해제는 ADMIN 이상입니다. 실제 신원 조회는 SUPER_ADMIN만 가능하며 감사 기록을 남깁니다. 닉네임 기반 운영과 실명 기반 사용자 검색을 구분합니다.
9. **투표:** ADMIN의 익명 투표 제출 내역에서는 작성자 신원을 숨깁니다. SUPER_ADMIN은 확인할 수 있습니다. 응답이 있는 투표는 SUPER_ADMIN이 `force=true`로 요청해야 삭제할 수 있습니다. 사용자 결과 조회는 투표의 결과 공개 조건도 적용됩니다.
10. **공개 시각·소유권:** 공연·타임테이블의 사용자 GET은 관리자 토큰이어도 공개 규칙을 적용합니다. 공지·분실물 관리 권한은 작성자 본인에게만 한정되지 않습니다. 생일 쪽지 사용자 삭제의 소유권 제한과 구분합니다.
11. **탈퇴:** 마지막 SUPER_ADMIN은 인계 관련 제한으로 탈퇴·축제 정보 삭제가 거부될 수 있습니다. SSO 자체 계정 정책과 축제 로컬 정책은 각각 적용됩니다.

## 3. API 상세표의 조건 코드

| 코드 | 의미 |
|---|---|
| PUBLIC | 역할 제한 없음. 선택 Bearer를 보냈다면 인증 오류가 발생할 수 있음 |
| LOGIN | 유효한 Bearer 필요. 본인 API는 본인에게만 적용 |
| STUDENT | 로그인 + 학생 인증. 채팅 운영 상태·투표 공개/참여 조건·본인 글 조건 등도 적용 |
| SCHOOL | 가입 전 학교 SSO 흐름. 역할 대신 state·콜백·임시 세션 검증 필요 |
| REFRESH | Bearer 대신 유효한 API Refresh 쿠키 필요. SSO 현재 계정 상태도 검사 |
| OPERATOR | BOOTH_MANAGER 또는 STAFF 이상. 개인정보 표시 범위는 역할별로 다름 |
| STAFF | STAFF·ADMIN·SUPER_ADMIN 허용 |
| ADMIN | ADMIN·SUPER_ADMIN 허용 |
| SUPER | SUPER_ADMIN만 허용 |
| STAMP | BOOTH_MANAGER는 담당 부스만. STAFF 단독 불가, ADMIN 이상 허용 |
| ROLE | ADMIN 이상이되 위 권한 변경 대상 제한 적용 |
| MUTE | STAFF는 차단만, ADMIN 이상은 차단·해제 가능 |
| POLL_DELETE | ADMIN은 응답 없는 투표만, SUPER_ADMIN은 force 조건으로 응답 있는 투표도 삭제 |
| POLL_IDENTITY | ADMIN은 익명 투표 신원 제외, SUPER_ADMIN은 신원 확인 가능 |

## 4. 관리자 웹 전용 기능

아래 경로는 HTML 화면·폼입니다. POST 변경에는 관리자 쿠키와 CSRF가 필요하며 실패를 반드시 JSON으로 반환하는 것은 아닙니다. 전체 메서드·경로는 [관리자 웹 색인](admin-web-api.md)에 정리되어 있습니다.

| 경로·기능 | 허용 역할 | 사용 불가 역할·조건 |
|---|---|---|
| `/admin`, `/admin/qr`, `POST /admin/qr/scan` | B·S·A·SA | USER·비로그인 |
| `POST /admin/qr/search` | S·A·SA | USER·B·비로그인 |
| `GET /admin/qr/users` 전체 목록·필터 | A·SA | USER·B·S·비로그인 |
| `/admin/qr/users/{userUuid}/role` | A·SA | 하위 역할 차단, ROLE 조건 적용 |
| `/admin/qr/users/{userUuid}/school-verification` | A·SA | 하위 역할 차단 |
| `/admin/school-verifications/**` | SA | A 이하 차단 |
| `/admin/student-fees/**` | SA | A 이하 차단 |
| `/admin/wristbands`, `/scan`, `/search`, `/issue` | S·A·SA | B·USER 차단, 지급 대상 학생 인증 필수 |
| `/admin/wristbands/manage`, `/records/{id}`, `/records/{id}/revoke` | A·SA | S 이하 차단, 철회 사유·버전 필요 |
| `/admin/stamps`, `/admin/stamps/qr/lookup`, `/admin/stamps/qr/action` | 담당 B·A·SA | STAFF 단독·USER 차단 |
| `POST /admin/stamps/user/action` | A·SA | B·S·USER 차단 |
| `/admin/booths/**`, `/admin/performances/**`, `/admin/timetable/**`, `/admin/sponsors/**`, `/admin/polls/**` | A·SA | S 이하 차단, 투표 익명·강제 삭제 조건 별도 |
| `/admin/notices/**`, `/admin/lost-items/**`, `/admin/birthday-messages/**` | S·A·SA | B·USER 차단 |
| `/admin/bamboo` 및 메시지 상태·닉네임·차단 | S·A·SA | B·USER 차단, STAFF 차단 해제 불가 |
| `/admin/bamboo/settings`, `/admin/bamboo/participants/mute` | A·SA | S 이하 차단 |
| `/admin/bamboo/messages/{id}/author`, `/admin/bamboo/users/**` | SA | A 이하 차단 |
| `/admin/system`, `/admin/system/snapshot` | SA | A 이하 차단 |
| `/admin/swagger-ui.html`, `/admin/swagger-ui/**`, `/admin/v3/api-docs*` | A·SA | S 이하 차단 |

`GET /admin/login` 화면 자체는 공개입니다. 로그인 성공에 필요한 관리자 권한은 B·S·A·SA이며 Swagger에서 들어와도 같은 로그인 화면을 사용합니다. Swagger 접근 허용은 로그인 후 별도로 검사합니다. `POST /admin/logout`은 관리자 쿠키 정리 흐름이며 CSRF가 필요합니다.

## 5. 개인정보 표시와 오류 처리

| 일반 QR·검색 조회자 | 표시 범위 |
|---|---|
| B | 마스킹 이름·학번, 학과·학년. 학생 인증·납부 상태는 생략 |
| S | 마스킹 이름·학번, 학과·학년, 학생 인증·납부 상태. UUID·연락처·권한 목록 생략 |
| A | 이름·학번·학과·학년·이메일·전화번호·UUID·축제 권한 및 인증·납부 상태 |
| SA | A 정보 + 로그인 ID·SSO 역할·계정 상태·재학 상태·생년월일·생성/변경 시각 |

위 표는 일반 QrUserView 기준입니다. 생일 쪽지 관리자 프로필·스탬프·팔찌 조회는 각각의 DTO와 마스킹 규칙을 따릅니다. 전체 회원 화면은 이름·학번·학과 요약을 기본 표시하며, 펼치면 조회자의 권한에 허용된 상세 정보가 표시됩니다. 일반 QR JSON에는 팔찌 필드가 없으며 관리자 HTML에만 표시합니다. 본인 팔찌 API는 별도로 있습니다.

- 인증 실패는 대체로 401, 역할·학생 인증 제한은 403입니다. `SCHOOL_VERIFICATION_REQUIRED`는 로그인 해제 대신 학생 인증으로 안내합니다.
- `USER_SEARCH_FORBIDDEN`은 검색 권한 부족과 전체 목록 권한 부족에서 모두 사용할 수 있으므로 요청 경로도 구분합니다.
- `SWAGGER_ROLE_REQUIRED`, `STAMP_MANAGE_FORBIDDEN`, `SUPER_ADMIN_REQUIRED` 등 code로 안내합니다. 변경 충돌·중복·최고 관리자 인계 제한은 409 등 다른 상태일 수 있습니다.
- 버튼 숨김은 UX이며 서버 검사를 대신하지 않습니다. 403 뒤에 같은 요청을 자동 반복하지 않습니다.

## 6. 근거 코드와 유지보수

- [관리자 인증·Swagger 허용 목록](../src/main/java/org/syu_likelion/Festa_2026/admin/AdminAccessService.java), [전체 회원 목록 제한](../src/main/java/org/syu_likelion/Festa_2026/qr/UserDirectoryService.java)
- [QR 조회·검색·마스킹](../src/main/java/org/syu_likelion/Festa_2026/qr/QrService.java), [역할 변경·학생 인증 직접 변경](../src/main/java/org/syu_likelion/Festa_2026/user/FestivalUserService.java), [학교 승인 API](../src/main/java/org/syu_likelion/Festa_2026/user/SchoolVerificationAdminController.java)
- [스탬프 담당 부스 검사](../src/main/java/org/syu_likelion/Festa_2026/stamp/StampService.java), [팔찌 권한](../src/main/java/org/syu_likelion/Festa_2026/wristband/WristbandService.java)
- [대나무숲 관리자 권한](../src/main/java/org/syu_likelion/Festa_2026/bamboo/BambooAdminService.java), [투표 신원·강제 삭제](../src/main/java/org/syu_likelion/Festa_2026/poll/PollService.java)

권한 변경 시 이 문서, API 색인, 도메인 문서와 서버 검사를 함께 갱신합니다. 아래 상세표는 기존 API 색인의 132개 메서드·경로를 모두 포함하며 관리자 JSON 2개와 학교 콜백 1개도 포함합니다. 관리자 HTML 화면 전체를 REST API 개수에 합산하지 않습니다. 이번 작업은 문서화이며 서버 권한을 변경하지 않습니다.

## 7. 메서드·경로별 전체 API 허용표

표의 B·S·A·SA 약어 및 O/X/C의 의미는 1절, 조건 코드는 3절을 따릅니다.


### Timetable

| Method | Path | anonymous | USER | B | S | A | SA | Policy |
|---|---|---|---|---|---|---|---|---|
| GET | `/api/timetable` | X | O | O | O | O | O | LOGIN |
| GET | `/api/timetable/{id}` | X | O | O | O | O | O | LOGIN |
| GET | `/api/timetable/admin` | X | X | X | X | O | O | ADMIN |
| POST | `/api/timetable` | X | X | X | X | O | O | ADMIN |
| PATCH | `/api/timetable/{id}` | X | X | X | X | O | O | ADMIN |
| DELETE | `/api/timetable/{id}` | X | X | X | X | O | O | ADMIN |

### Wristband

| Method | Path | anonymous | USER | B | S | A | SA | Policy |
|---|---|---|---|---|---|---|---|---|
| GET | `/api/users/me/wristband` | X | O | O | O | O | O | LOGIN |

### Admin Console

| Method | Path | anonymous | USER | B | S | A | SA | Policy |
|---|---|---|---|---|---|---|---|---|
| GET | `/admin/bamboo/cursor` | X | X | X | O | O | O | STAFF |
| GET | `/admin/system/snapshot` | X | X | X | X | X | O | SUPER |

### Account Recovery

| Method | Path | anonymous | USER | B | S | A | SA | Policy |
|---|---|---|---|---|---|---|---|---|
| POST | `/api/auth/email/find-id/verify` | O | O | O | O | O | O | PUBLIC |
| POST | `/api/auth/email/reset-password/verify` | O | O | O | O | O | O | PUBLIC |
| POST | `/api/auth/email/send` | O | O | O | O | O | O | PUBLIC |

### Auth

| Method | Path | anonymous | USER | B | S | A | SA | Policy |
|---|---|---|---|---|---|---|---|---|
| GET | `/api/auth/check/email` | O | O | O | O | O | O | PUBLIC |
| GET | `/api/auth/check/login-id` | O | O | O | O | O | O | PUBLIC |
| GET | `/api/auth/check/phone` | O | O | O | O | O | O | PUBLIC |
| GET | `/api/auth/check/student-no` | O | O | O | O | O | O | PUBLIC |
| POST | `/api/auth/login` | O | O | O | O | O | O | PUBLIC |
| POST | `/api/auth/logout` | O | O | O | O | O | O | PUBLIC |
| POST | `/api/auth/signup` | O | O | O | O | O | O | PUBLIC |
| POST | `/api/auth/signup/email/send` | O | O | O | O | O | O | PUBLIC |
| POST | `/api/auth/signup/email/verify` | O | O | O | O | O | O | PUBLIC |
| POST | `/api/auth/token/refresh` | C | C | C | C | C | C | REFRESH |

### Bamboo

| Method | Path | anonymous | USER | B | S | A | SA | Policy |
|---|---|---|---|---|---|---|---|---|
| GET | `/api/bamboo` | X | C | C | C | C | C | STUDENT |
| GET | `/api/bamboo/messages` | X | C | C | C | C | C | STUDENT |
| POST | `/api/bamboo/messages` | X | C | C | C | C | C | STUDENT |
| POST | `/api/bamboo/messages/{id}/report` | X | C | C | C | C | C | STUDENT |
| POST | `/api/bamboo/nickname` | X | C | C | C | C | C | STUDENT |
| GET | `/api/bamboo/nickname/suggest` | X | C | C | C | C | C | STUDENT |

### Bamboo Admin

| Method | Path | anonymous | USER | B | S | A | SA | Policy |
|---|---|---|---|---|---|---|---|---|
| PATCH | `/api/admin/bamboo/messages` | X | X | X | O | O | O | STAFF |
| GET | `/api/admin/bamboo/messages/{id}/author` | X | X | X | X | X | O | SUPER |
| PATCH | `/api/admin/bamboo/messages/{id}/author-nickname` | X | X | X | O | O | O | STAFF |
| POST | `/api/admin/bamboo/messages/{id}/mute-author` | X | X | X | C | O | O | MUTE |
| GET | `/api/admin/bamboo/reports` | X | X | X | O | O | O | STAFF |
| GET | `/api/admin/bamboo/settings` | X | X | X | O | O | O | STAFF |
| PATCH | `/api/admin/bamboo/settings` | X | X | X | X | O | O | ADMIN |

### Birthday Message

| Method | Path | anonymous | USER | B | S | A | SA | Policy |
|---|---|---|---|---|---|---|---|---|
| GET | `/api/birthday-messages` | X | C | C | C | C | C | STUDENT |
| POST | `/api/birthday-messages` | X | C | C | C | C | C | STUDENT |
| GET | `/api/birthday-messages/me` | X | C | C | C | C | C | STUDENT |
| GET | `/api/birthday-messages/{id}` | X | C | C | C | C | C | STUDENT |
| DELETE | `/api/birthday-messages/{id}` | X | C | C | C | C | C | STUDENT |
| PUT | `/api/birthday-messages/{id}/heart` | X | C | C | C | C | C | STUDENT |
| DELETE | `/api/birthday-messages/{id}/heart` | X | C | C | C | C | C | STUDENT |

### Birthday Message Admin

| Method | Path | anonymous | USER | B | S | A | SA | Policy |
|---|---|---|---|---|---|---|---|---|
| GET | `/api/admin/birthday-messages` | X | X | X | O | O | O | STAFF |
| DELETE | `/api/admin/birthday-messages/{id}` | X | X | X | O | O | O | STAFF |
| GET | `/api/admin/birthday-messages/{id}/hearts` | X | X | X | O | O | O | STAFF |

### Booth

| Method | Path | anonymous | USER | B | S | A | SA | Policy |
|---|---|---|---|---|---|---|---|---|
| GET | `/api/booths` | O | O | O | O | O | O | PUBLIC |
| POST | `/api/booths` | X | X | X | X | O | O | ADMIN |
| GET | `/api/booths/{id}` | O | O | O | O | O | O | PUBLIC |
| DELETE | `/api/booths/{id}` | X | X | X | X | O | O | ADMIN |
| PATCH | `/api/booths/{id}` | X | X | X | X | O | O | ADMIN |
| POST | `/api/booths/{id}/favorite` | X | O | O | O | O | O | LOGIN |
| DELETE | `/api/booths/{id}/favorite` | X | O | O | O | O | O | LOGIN |
| POST | `/api/booths/{id}/images` | X | X | X | X | O | O | ADMIN |
| PATCH | `/api/booths/{id}/media/order` | X | X | X | X | O | O | ADMIN |
| DELETE | `/api/booths/{id}/media/{mediaId}` | X | X | X | X | O | O | ADMIN |
| POST | `/api/booths/{id}/videos` | X | X | X | X | O | O | ADMIN |
| GET | `/api/users/me/favorite-booths` | X | O | O | O | O | O | LOGIN |

### Frontend Analytics

| Method | Path | anonymous | USER | B | S | A | SA | Policy |
|---|---|---|---|---|---|---|---|---|
| POST | `/api/analytics/events` | O | O | O | O | O | O | PUBLIC |

### Frontend Presence

| Method | Path | anonymous | USER | B | S | A | SA | Policy |
|---|---|---|---|---|---|---|---|---|
| POST | `/api/presence/heartbeat` | O | O | O | O | O | O | PUBLIC |

### Lost Item

| Method | Path | anonymous | USER | B | S | A | SA | Policy |
|---|---|---|---|---|---|---|---|---|
| GET | `/api/lost-items` | O | O | O | O | O | O | PUBLIC |
| POST | `/api/lost-items` | X | X | X | O | O | O | STAFF |
| GET | `/api/lost-items/{id}` | O | O | O | O | O | O | PUBLIC |
| DELETE | `/api/lost-items/{id}` | X | X | X | O | O | O | STAFF |
| PATCH | `/api/lost-items/{id}` | X | X | X | O | O | O | STAFF |
| PATCH | `/api/lost-items/{id}/pin` | X | X | X | O | O | O | STAFF |
| PATCH | `/api/lost-items/{id}/status` | X | X | X | O | O | O | STAFF |

### Notice

| Method | Path | anonymous | USER | B | S | A | SA | Policy |
|---|---|---|---|---|---|---|---|---|
| GET | `/api/notices` | O | O | O | O | O | O | PUBLIC |
| POST | `/api/notices` | X | X | X | O | O | O | STAFF |
| GET | `/api/notices/{id}` | O | O | O | O | O | O | PUBLIC |
| DELETE | `/api/notices/{id}` | X | X | X | O | O | O | STAFF |
| PATCH | `/api/notices/{id}` | X | X | X | O | O | O | STAFF |
| PATCH | `/api/notices/{id}/pin` | X | X | X | O | O | O | STAFF |

### Performance

| Method | Path | anonymous | USER | B | S | A | SA | Policy |
|---|---|---|---|---|---|---|---|---|
| GET | `/api/performances` | X | O | O | O | O | O | LOGIN |
| POST | `/api/performances` | X | X | X | X | O | O | ADMIN |
| GET | `/api/performances/{id}` | X | O | O | O | O | O | LOGIN |
| DELETE | `/api/performances/{id}` | X | X | X | X | O | O | ADMIN |
| PATCH | `/api/performances/{id}` | X | X | X | X | O | O | ADMIN |
| POST | `/api/performances/{id}/images` | X | X | X | X | O | O | ADMIN |
| DELETE | `/api/performances/{id}/media/{mediaId}` | X | X | X | X | O | O | ADMIN |
| POST | `/api/performances/{id}/videos` | X | X | X | X | O | O | ADMIN |

### Poll

| Method | Path | anonymous | USER | B | S | A | SA | Policy |
|---|---|---|---|---|---|---|---|---|
| GET | `/api/polls` | X | C | C | C | C | C | STUDENT |
| GET | `/api/polls/{id}` | X | C | C | C | C | C | STUDENT |
| GET | `/api/polls/{id}/results` | X | C | C | C | C | C | STUDENT |
| POST | `/api/polls/{id}/submissions` | X | C | C | C | C | C | STUDENT |
| GET | `/api/polls/{id}/submissions/me` | X | C | C | C | C | C | STUDENT |

### Poll Admin

| Method | Path | anonymous | USER | B | S | A | SA | Policy |
|---|---|---|---|---|---|---|---|---|
| GET | `/api/admin/polls` | X | X | X | X | O | O | ADMIN |
| POST | `/api/admin/polls` | X | X | X | X | O | O | ADMIN |
| GET | `/api/admin/polls/{id}` | X | X | X | X | C | O | POLL_IDENTITY |
| PUT | `/api/admin/polls/{id}` | X | X | X | X | O | O | ADMIN |
| DELETE | `/api/admin/polls/{id}` | X | X | X | X | C | C | POLL_DELETE |
| POST | `/api/admin/polls/{id}/close` | X | X | X | X | O | O | ADMIN |
| POST | `/api/admin/polls/{id}/options/{optionId}/image` | X | X | X | X | O | O | ADMIN |
| DELETE | `/api/admin/polls/{id}/options/{optionId}/image` | X | X | X | X | O | O | ADMIN |
| POST | `/api/admin/polls/{id}/questions/{questionId}/media` | X | X | X | X | O | O | ADMIN |
| PATCH | `/api/admin/polls/{id}/questions/{questionId}/media/order` | X | X | X | X | O | O | ADMIN |
| DELETE | `/api/admin/polls/{id}/questions/{questionId}/media/{mediaId}` | X | X | X | X | O | O | ADMIN |
| PATCH | `/api/admin/polls/{id}/settings` | X | X | X | X | O | O | ADMIN |

### QR

| Method | Path | anonymous | USER | B | S | A | SA | Policy |
|---|---|---|---|---|---|---|---|---|
| POST | `/api/qr/scan` | X | X | O | O | O | O | OPERATOR |
| POST | `/api/qr/search` | X | X | X | O | O | O | STAFF |
| POST | `/api/qr/tokens` | X | O | O | O | O | O | LOGIN |
| PATCH | `/api/qr/users/{userUuid}/role` | X | X | X | X | C | C | ROLE |
| PATCH | `/api/qr/users/{userUuid}/school-verification` | X | X | X | X | O | O | ADMIN |

### School SSO

| Method | Path | anonymous | USER | B | S | A | SA | Policy |
|---|---|---|---|---|---|---|---|---|
| GET | `/api/auth/school/authorize` | C | C | C | C | C | C | SCHOOL |
| GET | `/api/auth/school/profile` | C | C | C | C | C | C | SCHOOL |
| DELETE | `/api/auth/school/profile` | C | C | C | C | C | C | SCHOOL |
| POST | `/api/users/me/school-verification/authorize` | X | O | O | O | O | O | LOGIN |
| GET | `/api/users/me/school-verification/department` | X | O | O | O | O | O | LOGIN |
| POST | `/api/users/me/school-verification/department/confirm` | X | O | O | O | O | O | LOGIN |
| GET | `/auth/sso/callback` | C | C | C | C | C | C | SCHOOL |

### School Verification Admin

| Method | Path | anonymous | USER | B | S | A | SA | Policy |
|---|---|---|---|---|---|---|---|---|
| GET | `/api/admin/school-verifications` | X | X | X | X | X | O | SUPER |
| DELETE | `/api/admin/school-verifications/{id}` | X | X | X | X | X | O | SUPER |
| POST | `/api/admin/school-verifications/{id}/approve` | X | X | X | X | X | O | SUPER |

### Sponsor

| Method | Path | anonymous | USER | B | S | A | SA | Policy |
|---|---|---|---|---|---|---|---|---|
| GET | `/api/sponsors` | O | O | O | O | O | O | PUBLIC |
| POST | `/api/sponsors` | X | X | X | X | O | O | ADMIN |
| GET | `/api/sponsors/{id}` | O | O | O | O | O | O | PUBLIC |
| PUT | `/api/sponsors/{id}` | X | X | X | X | O | O | ADMIN |
| DELETE | `/api/sponsors/{id}` | X | X | X | X | O | O | ADMIN |

### Stamp

| Method | Path | anonymous | USER | B | S | A | SA | Policy |
|---|---|---|---|---|---|---|---|---|
| GET | `/api/booths/{boothId}/stamps/history` | X | X | C | X | O | O | STAMP |
| POST | `/api/booths/{boothId}/stamps/qr/grant` | X | X | C | X | O | O | STAMP |
| POST | `/api/booths/{boothId}/stamps/qr/lookup` | X | X | C | X | O | O | STAMP |
| POST | `/api/booths/{boothId}/stamps/qr/revoke` | X | X | C | X | O | O | STAMP |
| POST | `/api/booths/{boothId}/stamps/users/{userUuid}/grant` | X | X | X | X | O | O | ADMIN |
| POST | `/api/booths/{boothId}/stamps/users/{userUuid}/revoke` | X | X | X | X | O | O | ADMIN |
| GET | `/api/users/me/stamps` | X | O | O | O | O | O | LOGIN |

### Password Change

| Method | Path | anonymous | USER | B | S | A | SA | Policy |
|---|---|---|---|---|---|---|---|---|
| PATCH | `/api/users/me/password` | X | O | O | O | O | O | LOGIN |

### User

| Method | Path | anonymous | USER | B | S | A | SA | Policy |
|---|---|---|---|---|---|---|---|---|
| GET | `/api/users/me` | X | O | O | O | O | O | LOGIN |
| DELETE | `/api/users/me` | X | O | O | O | O | O | LOGIN |
| PATCH | `/api/users/me/email` | X | O | O | O | O | O | LOGIN |
| POST | `/api/users/me/email/verification` | X | O | O | O | O | O | LOGIN |
| POST | `/api/users/me/email/verification/confirm` | X | O | O | O | O | O | LOGIN |
| DELETE | `/api/users/me/festival` | X | O | O | O | O | O | LOGIN |
| PATCH | `/api/users/me/profile` | X | O | O | O | O | O | LOGIN |
