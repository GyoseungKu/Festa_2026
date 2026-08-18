# 생일축하 쪽지·하트 API

공통 인증과 오류 처리는 [공통 API 규약](frontend-api-common.md)을 따릅니다.

## 정책

- 목록·상세는 공개입니다.
- 작성·내 쪽지·본인 삭제·하트는 Bearer 인증이 필요합니다.
- 활성 쪽지는 사용자당 하나이며 수정 API는 없습니다.
- 삭제하면 연결된 하트도 삭제되고 새 쪽지를 작성할 수 있습니다.
- 본문은 trim 후 1~100자입니다.
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
Authorization: Bearer ACCESS_TOKEN  # 선택
```

- `sort`: `LATEST`(기본), `OLDEST`, `MOST_LIKED`
- `page`: 0부터 시작
- `size`: 기본 30, 최대 100
- 비로그인 요청은 `heartedByMe=false`, `mine=false`
- 로그인 토큰을 보내면 두 값이 사용자 기준으로 계산됨

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
