# 프런트엔드 API 문서

React 프런트에서 Festa API를 연동할 때 사용하는 문서 모음입니다. 실제 필드 스키마는 서버 DTO, 세부 확인은 Swagger UI(`/swagger-ui.html`)가 기준입니다.

## 먼저 읽을 문서

1. [공통 API 규약](frontend-api-common.md)
2. [인증·회원가입](frontend-auth-api.md)
3. 학교 SSO를 사용하면 [프런트엔드 학교 SSO 연동](frontend-school-sso.md)
4. 구현할 기능에 해당하는 도메인 문서

## 문서 목록

| 기능 | 문서 |
|---|---|
| 공통 헤더·오류·토큰 갱신 | [공통 API 규약](frontend-api-common.md) |
| 회원가입·로그인·로그아웃 | [인증 API](frontend-auth-api.md) |
| 회원가입 학적정보 자동입력·로그인 후 학생 인증 | [프런트엔드 학교 SSO](frontend-school-sso.md) |
| 아이디 찾기·비밀번호 재설정 | [계정 복구](frontend-account-recovery-api.md) |
| 내 정보·이메일·탈퇴 | [내 정보 API](frontend-user-api.md) |
| 학생 인증·관리자 승인 | [학생 인증 기능](student-verification.md) |
| 로그인 사용자 비밀번호 변경 | [비밀번호 변경](frontend-password-change-api.md) |
| 부스 지도·찜·미디어 | [부스 API](frontend-booths-api.md) |
| 스탬프판·지급·회수·이력 | [스탬프 API](frontend-stamps-api.md) |
| 공연 일정·미디어 | [공연 API](frontend-performances-api.md) |
| 투표·응답 폼·실시간 결과 | [투표 API](frontend-polls-api.md) |
| 동적 사용자 QR | [QR API](frontend-qr-api.md) |
| 분실물 공지 | [분실물 API](frontend-lost-items-api.md) |
| 생일축하 쪽지·하트 | [생일축하 API](frontend-birthday-messages-api.md) |
| 대나무숲 익명 채팅·신고·관리자 차단 감사 이력 | [대나무숲 API](frontend-bamboo-api.md) |
| 화면·행동 분석 이벤트 | [분석 이벤트](frontend-analytics-api.md) |
| 현재 접속 추정 heartbeat | [Presence](frontend-presence-api.md) |

## 구현 체크리스트

- 모든 API 요청에 `credentials: "include"`를 공통 적용합니다.
- 로그인 상태에서만 `Authorization: Bearer <accessToken>`을 추가합니다.
- 모든 응답에서 `X-Access-Token`을 확인해 메모리 토큰을 교체합니다.
- `204 No Content` 응답에 `response.json()`을 호출하지 않습니다.
- 실패 응답은 HTTP 상태만 보지 말고 JSON의 `code`로 분기합니다.
- `Instant`는 UTC ISO-8601로 받고 화면에서 `Asia/Seoul`로 변환합니다.
- `LocalTime`은 축제 당일의 `HH:mm:ss` 값이며 날짜를 붙여 해석하지 않습니다.
- multipart 요청에서 `FormData`를 사용할 때 `Content-Type`을 직접 지정하지 않습니다.
- Access/Refresh Token, 비밀번호, 인증번호, 개인정보를 로그·analytics·URL에 넣지 않습니다.
