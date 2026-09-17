# API 전체 색인

[문서 목차](README.md) · [프런트 연동 시작하기](frontend-getting-started.md)

**이 문서의 순서**

- [Admin Console](#admin-console)
- [Account Recovery](#account-recovery)
- [Auth](#auth)
- [Bamboo](#bamboo)
- [Bamboo Admin](#bamboo-admin)
- [Birthday Message](#birthday-message)
- [Birthday Message Admin](#birthday-message-admin)
- [Booth](#booth)
- [Frontend Analytics](#frontend-analytics)
- [Frontend Presence](#frontend-presence)
- [Lost Item](#lost-item)
- [Notice](#notice)
- [Performance](#performance)
- [Poll](#poll)
- [Poll Admin](#poll-admin)
- [QR](#qr)
- [School SSO](#school-sso)
- [School Verification Admin](#school-verification-admin)
- [Sponsor](#sponsor)
- [Stamp](#stamp)
- [Password Change](#password-change)
- [User](#user)
- [Wristband](#wristband)

2026-09-14 테스트 애플리케이션의 OpenAPI와 컨트롤러를 대조한 목록입니다. 총 98개 경로, HTTP 메서드 기준 125개입니다. 이 중 `/api/**`는 122개이며 학교 콜백 1개와 관리자 웹 JSON 조회 2개를 포함합니다.

성공 코드는 컨트롤러의 선언 기준입니다. 오류·권한·생략/null 처리·서비스 검증은 연결된 도메인 문서를 함께 읽습니다. OpenAPI에 오류 응답이나 nullable이 생략되었다고 해서 실제로 발생하지 않는 것은 아닙니다. HTML 폼 경로는 [관리자 웹 경로](admin-web-api.md), 업로드 제한은 [업로드 규약](api-upload-limits.md)을 참고합니다.

API를 추가·변경하면 이 색인과 도메인 문서를 함께 수정합니다. `/admin/v3/api-docs`와 [OpenAPI 검증 테스트](../src/test/java/org/syu_likelion/Festa_2026/config/OpenApiDocumentationIntegrationTests.java)로 경로·메서드·성공 응답을 다시 대조할 수 있습니다.

2026-09-16 팔찌 본인 조회를 추가했습니다. 위 개수는 최초 검토 시점의 수치이며 이후 추가된 API는 아래 항목과 현재 Swagger를 확인합니다.

## Wristband

[팔찌 지급·조회 문서](frontend-wristbands-api.md)

| 메서드 | 경로 | 성공 코드·본문 | 요청 본문 |
|---|---|---|---|
| GET | `/api/users/me/wristband` | 200 · `MyStatus` (`issued`, `issuedAt`, `schoolVerified`) | 없음, Bearer 인증 |


## Admin Console

[연동 문서](admin-web-api.md)

| 메서드 | 경로 | 성공 코드·본문 | 요청 본문 |
|---|---|---|---|
| GET | `/admin/bamboo/cursor` | 200 / object | 없음 |
| GET | `/admin/system/snapshot` | 200 / SystemSnapshot | 없음 |

## Account Recovery

[연동 문서](frontend-account-recovery-api.md)

| 메서드 | 경로 | 성공 코드·본문 | 요청 본문 |
|---|---|---|---|
| POST | `/api/auth/email/find-id/verify` | 200 / FindIdResponse | application/json |
| POST | `/api/auth/email/reset-password/verify` | 200 / MessageResponse | application/json |
| POST | `/api/auth/email/send` | 200 / MessageResponse | application/json |

## Auth

[연동 문서](frontend-auth-api.md)

| 메서드 | 경로 | 성공 코드·본문 | 요청 본문 |
|---|---|---|---|
| GET | `/api/auth/check/email` | 200 / AvailabilityResponse | 없음 |
| GET | `/api/auth/check/login-id` | 200 / AvailabilityResponse | 없음 |
| GET | `/api/auth/check/phone` | 200 / AvailabilityResponse | 없음 |
| GET | `/api/auth/check/student-no` | 200 / AvailabilityResponse | 없음 |
| POST | `/api/auth/login` | 200 / TokenResponse | application/json |
| POST | `/api/auth/logout` | 204 / 본문 없음 | 없음 |
| POST | `/api/auth/signup` | 200 / SignupResponse | application/json |
| POST | `/api/auth/signup/email/send` | 200 / MessageResponse | application/json |
| POST | `/api/auth/signup/email/verify` | 200 / MessageResponse | application/json |
| POST | `/api/auth/token/refresh` | 200 / TokenResponse | 없음 |

## Bamboo

[연동 문서](frontend-bamboo-api.md)

| 메서드 | 경로 | 성공 코드·본문 | 요청 본문 |
|---|---|---|---|
| GET | `/api/bamboo` | 200 / BambooRoomResponse | 없음 |
| GET | `/api/bamboo/messages` | 200 / BambooStreamResponse | 없음 |
| POST | `/api/bamboo/messages` | 201 / BambooMessageResponse | application/json |
| POST | `/api/bamboo/messages/{id}/report` | 204 / 본문 없음 | application/json |
| POST | `/api/bamboo/nickname` | 201 / BambooNicknameResponse | application/json |
| GET | `/api/bamboo/nickname/suggest` | 200 / BambooNicknameResponse | 없음 |

## Bamboo Admin

[연동 문서](frontend-bamboo-api.md)

| 메서드 | 경로 | 성공 코드·본문 | 요청 본문 |
|---|---|---|---|
| PATCH | `/api/admin/bamboo/messages` | 200 / object | application/json |
| GET | `/api/admin/bamboo/messages/{id}/author` | 200 / BambooAuthorResponse | 없음 |
| PATCH | `/api/admin/bamboo/messages/{id}/author-nickname` | 200 / BambooNicknameResponse | application/json |
| POST | `/api/admin/bamboo/messages/{id}/mute-author` | 200 / BambooMuteResponse | application/json |
| GET | `/api/admin/bamboo/reports` | 200 / BambooAdminPageResponse | 없음 |
| GET | `/api/admin/bamboo/settings` | 200 / BambooSettingsResponse | 없음 |
| PATCH | `/api/admin/bamboo/settings` | 200 / BambooSettingsResponse | application/json |

## Birthday Message

[연동 문서](frontend-birthday-messages-api.md)

| 메서드 | 경로 | 성공 코드·본문 | 요청 본문 |
|---|---|---|---|
| GET | `/api/birthday-messages` | 200 / BirthdayMessagePageResponse | 없음 |
| POST | `/api/birthday-messages` | 201 / BirthdayMessageResponse | application/json |
| GET | `/api/birthday-messages/me` | 200 / MyBirthdayMessageResponse | 없음 |
| GET | `/api/birthday-messages/{id}` | 200 / BirthdayMessageResponse | 없음 |
| DELETE | `/api/birthday-messages/{id}` | 204 / 본문 없음 | 없음 |
| PUT | `/api/birthday-messages/{id}/heart` | 200 / HeartResponse | 없음 |
| DELETE | `/api/birthday-messages/{id}/heart` | 200 / HeartResponse | 없음 |

## Birthday Message Admin

[연동 문서](frontend-birthday-messages-api.md)

| 메서드 | 경로 | 성공 코드·본문 | 요청 본문 |
|---|---|---|---|
| GET | `/api/admin/birthday-messages` | 200 / AdminBirthdayMessagePageResponse | 없음 |
| DELETE | `/api/admin/birthday-messages/{id}` | 204 / 본문 없음 | 없음 |
| GET | `/api/admin/birthday-messages/{id}/hearts` | 200 / AdminHeartPageResponse | 없음 |

## Booth

[연동 문서](frontend-booths-api.md)

| 메서드 | 경로 | 성공 코드·본문 | 요청 본문 |
|---|---|---|---|
| GET | `/api/booths` | 200 / BoothSummaryResponse[] | 없음 |
| POST | `/api/booths` | 201 / BoothAdminResponse | application/json |
| GET | `/api/booths/{id}` | 200 / BoothDetailResponse | 없음 |
| DELETE | `/api/booths/{id}` | 204 / 본문 없음 | 없음 |
| PATCH | `/api/booths/{id}` | 200 / BoothAdminResponse | application/json |
| POST | `/api/booths/{id}/favorite` | 204 / 본문 없음 | 없음 |
| DELETE | `/api/booths/{id}/favorite` | 204 / 본문 없음 | 없음 |
| POST | `/api/booths/{id}/images` | 200 / BoothAdminResponse | multipart/form-data |
| PATCH | `/api/booths/{id}/media/order` | 200 / BoothAdminResponse | application/json |
| DELETE | `/api/booths/{id}/media/{mediaId}` | 200 / BoothAdminResponse | 없음 |
| POST | `/api/booths/{id}/videos` | 200 / BoothAdminResponse | multipart/form-data |
| GET | `/api/users/me/favorite-booths` | 200 / BoothSummaryResponse[] | 없음 |

## Frontend Analytics

[연동 문서](frontend-analytics-api.md)

| 메서드 | 경로 | 성공 코드·본문 | 요청 본문 |
|---|---|---|---|
| POST | `/api/analytics/events` | 202 / EventBatchResponse | application/json |

## Frontend Presence

[연동 문서](frontend-presence-api.md)

| 메서드 | 경로 | 성공 코드·본문 | 요청 본문 |
|---|---|---|---|
| POST | `/api/presence/heartbeat` | 204 / 본문 없음 | application/json |

## Lost Item

[연동 문서](frontend-lost-items-api.md)

| 메서드 | 경로 | 성공 코드·본문 | 요청 본문 |
|---|---|---|---|
| GET | `/api/lost-items` | 200 / LostItemPageResponse | 없음 |
| POST | `/api/lost-items` | 201 / LostItemResponse | multipart/form-data |
| GET | `/api/lost-items/{id}` | 200 / LostItemResponse | 없음 |
| DELETE | `/api/lost-items/{id}` | 204 / 본문 없음 | 없음 |
| PATCH | `/api/lost-items/{id}` | 200 / LostItemResponse | multipart/form-data |
| PATCH | `/api/lost-items/{id}/pin` | 200 / LostItemResponse | application/json |
| PATCH | `/api/lost-items/{id}/status` | 200 / LostItemResponse | application/json |

## Notice

[연동 문서](frontend-notices-api.md)

| 메서드 | 경로 | 성공 코드·본문 | 요청 본문 |
|---|---|---|---|
| GET | `/api/notices` | 200 / NoticePageResponse | 없음 |
| POST | `/api/notices` | 201 / NoticeResponse | multipart/form-data |
| GET | `/api/notices/{id}` | 200 / NoticeResponse | 없음 |
| DELETE | `/api/notices/{id}` | 204 / 본문 없음 | 없음 |
| PATCH | `/api/notices/{id}` | 200 / NoticeResponse | multipart/form-data |
| PATCH | `/api/notices/{id}/pin` | 200 / NoticeResponse | application/json |

## Performance

[연동 문서](frontend-performances-api.md)

| 메서드 | 경로 | 성공 코드·본문 | 요청 본문 |
|---|---|---|---|
| GET | `/api/performances` | 200 / PerformanceResponse[] | 없음 |
| POST | `/api/performances` | 201 / PerformanceResponse | application/json |
| GET | `/api/performances/{id}` | 200 / PerformanceResponse | 없음 |
| DELETE | `/api/performances/{id}` | 204 / 본문 없음 | 없음 |
| PATCH | `/api/performances/{id}` | 200 / PerformanceResponse | application/json |
| POST | `/api/performances/{id}/images` | 200 / PerformanceResponse | multipart/form-data |
| DELETE | `/api/performances/{id}/media/{mediaId}` | 200 / PerformanceResponse | 없음 |
| POST | `/api/performances/{id}/videos` | 200 / PerformanceResponse | multipart/form-data |

## Poll

[연동 문서](frontend-polls-api.md)

| 메서드 | 경로 | 성공 코드·본문 | 요청 본문 |
|---|---|---|---|
| GET | `/api/polls` | 200 / PollSummaryResponse[] | 없음 |
| GET | `/api/polls/{id}` | 200 / PollDetailResponse | 없음 |
| GET | `/api/polls/{id}/results` | 200 / PollResultResponse | 없음 |
| POST | `/api/polls/{id}/submissions` | 201 / SubmissionReceipt | application/json |
| GET | `/api/polls/{id}/submissions/me` | 200 / MySubmissionResponse[] | 없음 |

## Poll Admin

[연동 문서](frontend-polls-api.md)

| 메서드 | 경로 | 성공 코드·본문 | 요청 본문 |
|---|---|---|---|
| GET | `/api/admin/polls` | 200 / PollSummaryResponse[] | 없음 |
| POST | `/api/admin/polls` | 201 / PollDetailResponse | application/json |
| GET | `/api/admin/polls/{id}` | 200 / PollAdminDetailResponse | 없음 |
| PUT | `/api/admin/polls/{id}` | 200 / PollDetailResponse | application/json |
| DELETE | `/api/admin/polls/{id}` | 204 / 본문 없음 | 없음 |
| POST | `/api/admin/polls/{id}/close` | 200 / PollDetailResponse | 없음 |
| POST | `/api/admin/polls/{id}/options/{optionId}/image` | 200 / PollDetailResponse | multipart/form-data |
| DELETE | `/api/admin/polls/{id}/options/{optionId}/image` | 200 / PollDetailResponse | 없음 |
| POST | `/api/admin/polls/{id}/questions/{questionId}/media` | 200 / PollDetailResponse | multipart/form-data |
| PATCH | `/api/admin/polls/{id}/questions/{questionId}/media/order` | 200 / PollDetailResponse | application/json |
| DELETE | `/api/admin/polls/{id}/questions/{questionId}/media/{mediaId}` | 200 / PollDetailResponse | 없음 |
| PATCH | `/api/admin/polls/{id}/settings` | 200 / PollDetailResponse | application/json |

## QR

[연동 문서](frontend-qr-api.md)

| 메서드 | 경로 | 성공 코드·본문 | 요청 본문 |
|---|---|---|---|
| POST | `/api/qr/scan` | 200 / QrUserView | application/json |
| POST | `/api/qr/search` | 200 / UserSearchResponse | application/json |
| POST | `/api/qr/tokens` | 200 / QrTokenResponse | 없음 |
| PATCH | `/api/qr/users/{userUuid}/role` | 200 / UserRoleUpdateResponse | application/json |
| PATCH | `/api/qr/users/{userUuid}/school-verification` | 200 / UserSchoolVerificationUpdateResponse | application/json |

## School SSO

[연동 문서](frontend-school-sso.md)

| 메서드 | 경로 | 성공 코드·본문 | 요청 본문 |
|---|---|---|---|
| GET | `/api/auth/school/authorize` | 302 / 본문 없음 | 없음 |
| GET | `/api/auth/school/profile` | 200 / AcademicProfileResponse | 없음 |
| DELETE | `/api/auth/school/profile` | 204 / 본문 없음 | 없음 |
| POST | `/api/users/me/school-verification/authorize` | 200 / SchoolAuthorizationResponse | 없음 |
| GET | `/api/users/me/school-verification/department` | 200 / DepartmentMismatchResponse | 없음 |
| POST | `/api/users/me/school-verification/department/confirm` | 200 / MeResponse | 없음 |
| GET | `/auth/sso/callback` | 302 / 본문 없음 | 없음 |

## School Verification Admin

[연동 문서](frontend-user-api.md)

| 메서드 | 경로 | 성공 코드·본문 | 요청 본문 |
|---|---|---|---|
| GET | `/api/admin/school-verifications` | 200 / RequestResponse[] | 없음 |
| DELETE | `/api/admin/school-verifications/{id}` | 200 / 본문 없음 | 없음 |
| POST | `/api/admin/school-verifications/{id}/approve` | 200 / 본문 없음 | 없음 |

## Sponsor

[연동 문서](frontend-sponsors-api.md)

| 메서드 | 경로 | 성공 코드·본문 | 요청 본문 |
|---|---|---|---|
| GET | `/api/sponsors` | 200 / SponsorResponse[] | 없음 |
| POST | `/api/sponsors` | 201 / SponsorResponse | multipart/form-data |
| GET | `/api/sponsors/{id}` | 200 / SponsorResponse | 없음 |
| PUT | `/api/sponsors/{id}` | 200 / SponsorResponse | multipart/form-data |
| DELETE | `/api/sponsors/{id}` | 204 / 본문 없음 | 없음 |

## Stamp

[연동 문서](frontend-stamps-api.md)

| 메서드 | 경로 | 성공 코드·본문 | 요청 본문 |
|---|---|---|---|
| GET | `/api/booths/{boothId}/stamps/history` | 200 / BoothStampAdminResponse | 없음 |
| POST | `/api/booths/{boothId}/stamps/qr/grant` | 200 / StampTargetResponse | application/json |
| POST | `/api/booths/{boothId}/stamps/qr/lookup` | 200 / StampTargetResponse | application/json |
| POST | `/api/booths/{boothId}/stamps/qr/revoke` | 200 / StampTargetResponse | application/json |
| POST | `/api/booths/{boothId}/stamps/users/{userUuid}/grant` | 200 / StampTargetResponse | 없음 |
| POST | `/api/booths/{boothId}/stamps/users/{userUuid}/revoke` | 200 / StampTargetResponse | 없음 |
| GET | `/api/users/me/stamps` | 200 / MyStampBoardResponse | 없음 |

## Password Change

[연동 문서](frontend-password-change-api.md)

| 메서드 | 경로 | 성공 코드·본문 | 요청 본문 |
|---|---|---|---|
| PATCH | `/api/users/me/password` | 204 / 본문 없음 | application/json |

## User

[연동 문서](frontend-user-api.md)

| 메서드 | 경로 | 성공 코드·본문 | 요청 본문 |
|---|---|---|---|
| GET | `/api/users/me` | 200 / MeResponse | 없음 |
| DELETE | `/api/users/me` | 204 / 본문 없음 | 없음 |
| PATCH | `/api/users/me/email` | 200 / MessageResponse | application/json |
| POST | `/api/users/me/email/verification` | 200 / MessageResponse | application/json |
| POST | `/api/users/me/email/verification/confirm` | 200 / MessageResponse | application/json |
| DELETE | `/api/users/me/festival` | 204 / 본문 없음 | 없음 |
| PATCH | `/api/users/me/profile` | 200 / MeResponse | application/json |
