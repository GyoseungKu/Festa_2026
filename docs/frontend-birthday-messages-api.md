# 수야·수호 생일축하 쪽지 API

## 정책

- 목록과 상세는 로그인 없이 조회할 수 있습니다.
- 작성, 본인 삭제, 하트 추가·취소는 Bearer 로그인이 필요합니다.
- 활성 쪽지는 사용자당 하나만 작성할 수 있습니다.
- 수정 API는 없습니다.
- 삭제하면 쪽지와 연결된 하트가 삭제되며 새 쪽지를 다시 작성할 수 있습니다.
- 본문은 Unicode 문자 기준 최대 100자입니다.
- 본인 쪽지에는 하트를 누를 수 없습니다.
- 공개 작성자 정보는 학과, 마스킹 학번, 마스킹 이름뿐입니다.

## 공개 조회

```http
GET /api/birthday-messages?sort=LATEST&page=0&size=30
GET /api/birthday-messages/{id}
```

정렬값은 `LATEST`, `OLDEST`, `MOST_LIKED`입니다. 기본값은 `LATEST`, 최대 페이지 크기는 100입니다.

Authorization 헤더 없이 호출하면 `heartedByMe`와 `mine`은 `false`입니다. 정상 Bearer Token을 함께 보내면 현재 사용자를 기준으로 두 값이 계산됩니다.

```json
{
  "items": [
    {
      "id": 11,
      "content": "수야와 수호의 생일을 축하해!",
      "author": {
        "department": "컴퓨터공학부",
        "maskedStudentNo": "2024******",
        "maskedName": "홍*동"
      },
      "heartCount": 3,
      "heartedByMe": false,
      "mine": false,
      "createdAt": "2026-08-14T03:00:00Z"
    }
  ],
  "page": 0,
  "size": 30,
  "totalElements": 1,
  "totalPages": 1
}
```

## 작성과 내 쪽지

```http
POST /api/birthday-messages
Authorization: Bearer ACCESS_TOKEN
Content-Type: application/json

{"content":"수야와 수호의 생일을 축하해!"}
```

성공 시 `201 Created`입니다. 이미 활성 쪽지가 있으면 `409 BIRTHDAY_MESSAGE_ALREADY_EXISTS`입니다.

```http
GET /api/birthday-messages/me
DELETE /api/birthday-messages/{id}
```

`GET /me`는 `written`과 현재 쪽지를 반환합니다. 본인이 아닌 쪽지를 삭제하면 `403 BIRTHDAY_MESSAGE_DELETE_FORBIDDEN`입니다.

## 하트

```http
PUT /api/birthday-messages/{id}/heart
DELETE /api/birthday-messages/{id}/heart
```

두 API는 멱등입니다. 같은 하트를 여러 번 추가하거나 취소해도 한 번만 반영됩니다. 본인 글에는 `400 SELF_HEART_NOT_ALLOWED`를 반환합니다.

## 관리자 API

`STAFF`, `ADMIN`, `SUPER_ADMIN`만 사용할 수 있습니다.

```http
GET /api/admin/birthday-messages?sort=MOST_LIKED&page=0&size=30
GET /api/admin/birthday-messages/{id}/hearts?page=0&size=30
DELETE /api/admin/birthday-messages/{id}
```

- `STAFF`: 이름, 학번, 학과, 학년
- `ADMIN`: STAFF 정보와 전화번호, 이메일
- `SUPER_ADMIN`: UUID, 로그인 ID, 이메일, SSO 역할·계정 상태, 이름, 전화번호, 학번, 학과, 학년, 재학 상태, 생년월일, 계정 생성·수정일시, 축제 권한
- 하트 사용자 API에는 현재 하트를 유지 중인 사용자와 `heartedAt`이 표시됩니다.
- 개인정보 원본은 게시판 DB에 복사하지 않고 관리자 조회 시 SSO 배치 프로필 API에서 가져옵니다.

관리자 HTML 화면은 `/admin/birthday-messages`에서 사용할 수 있습니다.
