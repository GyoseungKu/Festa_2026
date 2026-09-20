# 생일축하 쪽지·하트 API

[문서 목차](README.md) · [프런트 연동 시작하기](frontend-getting-started.md)

공통 인증과 오류 처리는 [공통 API 규약](frontend-api-common.md)을 따릅니다.

## 학생 인증 필수

일반 사용자 API는 조회·참여 모두 로그인과 학생 인증이 필요합니다. `schoolVerificationStatus=VERIFIED`이고 인증 시각이 있어야 하며, 미인증·인증 회수 상태는 아래 오류를 반환합니다. 학생회비 납부 여부는 이용 조건이 아닙니다.

```http
HTTP/1.1 403 Forbidden
Content-Type: application/json

{"code":"SCHOOL_VERIFICATION_REQUIRED","message":"학생 인증 완료 후 이용할 수 있습니다."}
```

프런트는 `/api/users/me`의 `schoolVerified`로 진입 화면을 구성하고, 위 오류를 받으면 [학생 인증 흐름](frontend-school-sso.md)으로 안내합니다. 비로그인 요청은 `401`입니다. 일반 사용자 API에는 관리자 역할도 학생 인증 예외가 없으며, 별도 관리자 운영 API·페이지는 기존 역할 권한을 따릅니다. 경로와 성공 응답 구조는 동일하며, 이전에 허용되던 미인증 요청은 이제 `403`으로 거절됩니다.

## 정책

- 목록·상세를 포함한 모든 사용자 API에 Bearer 인증과 학생 인증이 필요합니다.
- 작성·내 쪽지·본인 삭제·하트는 Bearer 인증이 필요합니다.
- 활성 쪽지는 사용자당 하나이며 수정 API는 없습니다.
- 삭제하면 연결된 하트도 삭제되고 새 쪽지를 작성할 수 있습니다.
- 본문은 trim 후 1–100자입니다.
- 본인 쪽지에는 하트를 누를 수 없습니다.
- 공개 작성자는 학과·마스킹 학번·마스킹 이름만 제공합니다.

## 타입

```ts
type BirthdayMessage = {
  id: number;
  content: string;
  author: {
    department: string | null;
    maskedStudentNo: string | null;
    maskedName: string | null;
  };
  heartCount: number;
  heartedByMe: boolean;
  mine: boolean;
  createdAt: string;
};

type BirthdayMessagePage = {
  items: BirthdayMessage[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};
```

## 목록·상세

```http
GET /api/birthday-messages?sort=LATEST&page=0&size=30
GET /api/birthday-messages/{id}
Authorization: Bearer ACCESS_TOKEN  # 필수
```

- `sort`: `LATEST`(기본), `OLDEST`, `MOST_LIKED`
- `page`: 0부터 시작
- `size`: 기본 30, 최대 100
- 비로그인 요청은 `401`, 학생 미인증 요청은 `403 SCHOOL_VERIFICATION_REQUIRED`
- 학생 인증된 사용자의 `heartedByMe`, `mine`은 해당 사용자 기준으로 계산됨

목록은 `BirthdayMessagePage`, 상세는 `BirthdayMessage`를 반환합니다.

## 작성·내 쪽지·삭제

```http
POST /api/birthday-messages
Content-Type: application/json
Authorization: Bearer ACCESS_TOKEN

{ "content": "수야와 수호의 생일을 축하해!" }
```

성공은 `201`과 생성된 `BirthdayMessage`입니다.

```http
GET /api/birthday-messages/me
DELETE /api/birthday-messages/{id}
```

내 쪽지 응답:

```json
{
  "written": true,
  "message": {
    "id": 11,
    "content": "수야와 수호의 생일을 축하해!",
    "author": {
      "department": "컴퓨터공학부",
      "maskedStudentNo": "2024******",
      "maskedName": "홍*동"
    },
    "heartCount": 3,
    "heartedByMe": false,
    "mine": true,
    "createdAt": "2026-08-18T03:00:00Z"
  }
}
```

작성하지 않았으면 `{ "written": false, "message": null }`입니다. 삭제 성공은 `204`입니다.

## 하트

```http
PUT /api/birthday-messages/{id}/heart
DELETE /api/birthday-messages/{id}/heart
Authorization: Bearer ACCESS_TOKEN
```

두 요청은 멱등이며 성공 응답은 동일한 형태입니다.

```json
{ "messageId": 11, "heartCount": 4, "heartedByMe": true }
```

낙관적 업데이트를 하더라도 서버 응답의 `heartCount`, `heartedByMe`로 최종 동기화합니다.

## STAFF 이상 관리자 API

```http
GET /api/admin/birthday-messages?sort=MOST_LIKED&page=0&size=30
GET /api/admin/birthday-messages/{id}/hearts?page=0&size=30
DELETE /api/admin/birthday-messages/{id}
```

- 목록과 하트 사용자 목록 모두 최대 페이지 크기는 100입니다.
- `STAFF`: 이름, 학번, 학과, 학년
- `ADMIN`: STAFF 범위 + 전화번호, 이메일
- `SUPER_ADMIN`: UUID, 로그인 ID, SSO 상태·역할, 전체 프로필과 축제 권한
- 권한상 숨기는 필드는 `null`이며 JSON에서 생략될 수 있습니다.
- 개인정보 원본은 게시판 DB가 아니라 조회 시점 SSO 프로필에서 가져옵니다.

## 관리자 응답 세부 구조

관리자 원본 프로필은 조회 시 SSO에서 가져오지만 공개 `author`의 마스킹 이름·학번·학과는 작성 당시 스냅샷입니다. SSO 프로필 변경이나 SSO에서 직접 수행한 탈퇴만으로 공개 값이 갱신되지는 않습니다. 축제 이용 정보 삭제 또는 홈페이지의 `DELETE /api/users/me`를 통한 SSO 탈퇴 시에는 별도로 익명화됩니다.

관리자 목록의 `items`에는 `id`, `content`, `heartCount`, `createdAt`, `author: AdminUserView`가 들어가고 `mine`, `heartedByMe`는 없습니다. 하트 목록은 다음 구조입니다.

```ts
type AdminUserView = {
  viewerRole: "STAFF" | "ADMIN" | "SUPER_ADMIN";
  userUuid?: string;
  loginId?: string;
  email?: string;
  ssoRole?: string;
  status?: string;
  name?: string;
  phone?: string;
  studentNo?: string;
  department?: string;
  grade?: number;
  enrollment?: string;
  birthDate?: string;
  createdAt?: string;
  updatedAt?: string;
  festivalRoles?: string[];
};
type AdminHeartPage = {
  messageId: number;
  heartCount: number;
  items: Array<{ user: AdminUserView; heartedAt: string }>;
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};
```

`AdminUserView`의 선택 필드는 권한 또는 원본 값 누락에 따라 생략됩니다. `heartedAt`은 UTC Instant입니다.

## 주요 오류

| HTTP | code | 처리 |
|---|---|---|
| `400` | `BIRTHDAY_MESSAGE_CONTENT_REQUIRED` | 빈 본문 안내 |
| `400` | `BIRTHDAY_MESSAGE_CONTENT_TOO_LONG` | Unicode 문자 기준 최대 100자 안내 |
| `400` | `SELF_HEART_NOT_ALLOWED` | 본인 글 하트 UI 비활성화 |
| `403` | `BIRTHDAY_MESSAGE_DELETE_FORBIDDEN` | 본인 글이 아님 |
| `403` | `BIRTHDAY_MESSAGE_MANAGE_FORBIDDEN` | 관리자 권한 없음 |
| `404` | `BIRTHDAY_MESSAGE_NOT_FOUND` | 목록 새로고침 |
| `409` | `BIRTHDAY_MESSAGE_ALREADY_EXISTS` | 내 쪽지 화면으로 이동 |

관리자 HTML 화면은 `/admin/birthday-messages`입니다.
