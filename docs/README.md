# 개발 문서 목차

[프로젝트 소개·서버 실행](../README.md) · [프런트 연동 시작하기](frontend-getting-started.md)

사용자 프런트 연동, 관리자 웹, 서버 운영 문서를 용도별로 나눴습니다. 요청·응답 계약은 기능별 문서에서 확인하고 실제 필드 스키마는 서버 DTO 및 Swagger UI(`/admin/swagger-ui.html`)와 대조합니다. Swagger만으로 서비스 검증·권한별 필드 생략·외부 SSO 동작을 모두 판단하지 않습니다.

## 처음 연동하는 순서

1. [프런트 연동 시작하기](frontend-getting-started.md): 연결 설정 → 로그인 복구 → 화면별 API → 연동 확인 순서로 진행합니다.
2. [공통 API 규약](frontend-api-common.md): fetch 래퍼, 쿠키·헤더, 오류와 날짜 처리를 공통으로 구현합니다.
3. [인증 API](frontend-auth-api.md)와 [내 정보 API](frontend-user-api.md): 로그인·권한·사용자 상태를 연결합니다. 공개 조회 화면은 해당 기능 문서부터 시작해도 됩니다.
4. 아래에서 구현할 화면의 문서를 고릅니다. 학교 인증·파일 업로드를 사용할 때는 각각의 별도 가이드를 함께 읽습니다.

기능별 문서는 정책·타입·조회·변경·관리·오류 순서로 찾아 읽습니다. 긴 문서의 상단 목차에서 필요한 절로 바로 이동할 수 있습니다. 사용자 화면만 구현한다면 관리자·DB·배포 절은 필요할 때 참고합니다.

## 공통 계약과 API 찾기

이용약관·개인정보처리방침 새 창 연결은 [공개 약관 페이지](terms-pages.md)를 확인합니다.

| 기능 | 문서 |
|---|---|
| 공통 헤더·오류·토큰 갱신 | [공통 API 규약](frontend-api-common.md) |
| 전체 경로·메서드·성공 응답 | [API 전체 색인](api-endpoint-index.md) |
| 역할별 허용·차단·학생 인증·담당 부스 조건 | [권한별 API·관리자 기능 허용표](api-role-permissions.md) |
| Swagger 열람 로그인·API 테스트·오류 확인 | [Swagger 연동 가이드](frontend-swagger-guide.md) |
| 파일 형식·파트·개수·용량 | [업로드 규약](api-upload-limits.md) |
| 이메일 인증코드 재전송·429·남은 대기 시간 | [이메일 재전송 제한](frontend-email-cooldown.md) |

## 로그인·가입·마이페이지

| 기능 | 문서 |
|---|---|
| 회원가입·로그인·로그아웃 | [인증 API](frontend-auth-api.md) |
| 회원가입 학적정보 자동입력·로그인 후 학생 인증 | [프런트엔드 학교 SSO](frontend-school-sso.md) |
| 아이디 찾기·비밀번호 재설정 | [계정 복구](frontend-account-recovery-api.md) |
| 내 정보·이메일·탈퇴 | [내 정보 API](frontend-user-api.md) |
| 로그인 사용자 비밀번호 변경 | [비밀번호 변경](frontend-password-change-api.md) |

## 사용자 기능별 API

입장 팔찌 운영 및 사용자 본인 조회는 [팔찌 지급·조회](frontend-wristbands-api.md)를 확인합니다.

| 기능 | 문서 |
|---|---|
| 일반 공지·배너·첨부파일 | [일반 공지 API](frontend-notices-api.md) |
| 부스 지도·찜·미디어 | [부스 API](frontend-booths-api.md) |
| 공연 일정·미디어 | [공연 API](frontend-performances-api.md) |
| 독립 일정·공개 전 TBA·공연팀 연결 | [타임테이블 API](frontend-timetable-api.md) |
| 협찬사 공개 조회·이미지·부스 연결 | [협찬사 API](frontend-sponsors-api.md) |
| 동적 사용자 QR | [QR API](frontend-qr-api.md) |
| 스탬프판·지급·회수·이력 | [스탬프 API](frontend-stamps-api.md) |
| 투표·응답 폼·실시간 결과 | [투표 API](frontend-polls-api.md) |
| 분실물 공지 | [분실물 API](frontend-lost-items-api.md) |
| 생일축하 쪽지·하트 | [생일축하 API](frontend-birthday-messages-api.md) |
| 대나무숲 익명 채팅·신고·관리자 차단 감사 이력 | [대나무숲 API](frontend-bamboo-api.md) |
| 화면·행동 분석 이벤트 | [분석 이벤트](frontend-analytics-api.md) |
| 현재 접속 추정 heartbeat | [Presence](frontend-presence-api.md) |

## 관리자·서버 운영

| 기능 | 문서 |
|---|---|
| 관리자 쿠키·CSRF·HTML 폼 경로 | [관리자 웹 경로](admin-web-api.md) |
| 학생 인증·관리자 승인 | [학생 인증 기능](student-verification.md) |
| 학생회비 납부자 관리·자동 확인 | [학생회비 확인 정책](student-fees.md) |
| 관리자 공통 디자인·레이아웃 | [관리자 디자인](admin-design.md) |

## 검토 기록

특정 시점의 점검 결과입니다. 현재 연동 계약은 위 기능별 문서를 우선 확인합니다.

| 기능 | 문서 |
|---|---|
| 문서 검토 범위·발견 사항·구현 제약 | [API 문서 검토 기록](api-documentation-review.md) |
| 관리자 UI 점검 기록 | [관리자 UI 점검](admin-ui-review.md) |

## 구현 체크리스트

- 인증·학교 세션 API에는 `credentials: "include"`를 적용합니다. 인증이 필요 없는 heartbeat는 `omit`을 사용할 수 있습니다.
- 로그인 상태에서만 `Authorization: Bearer <accessToken>`을 추가합니다.
- 모든 응답에서 `X-Access-Token`을 확인해 메모리 토큰을 교체합니다.
- `204 No Content`와 본문 없는 `200` 응답에 `response.json()`을 호출하지 않습니다.
- 실패 응답은 HTTP 상태만 보지 말고 JSON의 `code`로 분기합니다.
- `Instant`는 UTC ISO-8601로 받고 화면에서 `Asia/Seoul`로 변환합니다.
- `LocalTime`은 날짜·시간대가 없는 `HH:mm:ss`입니다. SSO 프로필의 `LocalDateTime`도 UTC Instant와 구분합니다.
- multipart 요청에서 `FormData`를 사용할 때 `Content-Type`을 직접 지정하지 않습니다.
- Access/Refresh Token, 비밀번호, 인증번호, 개인정보를 로그·analytics·URL에 넣지 않습니다.
