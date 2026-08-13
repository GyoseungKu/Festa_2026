# 분실물 공지 API 연동 가이드

분실물 공지는 로그인하지 않은 사용자도 조회할 수 있습니다. 요청에 `Authorization` 헤더나 Refresh Cookie가 필요하지 않습니다.

## 목록 조회

```http
GET /api/lost-items?status=HOLDING&sort=NEWEST&page=0&size=20
```

- `status`: 선택값. `HOLDING`(보관 중), `RETURNED`(주인에게 돌아감)
- `sort`: `NEWEST`(최신순, 기본값), `OLDEST`(오래된순)
- `page`: 0부터 시작하며 기본값은 0
- `size`: 기본값 20, 최대 100
- `status`를 생략하면 보관 중과 반환 완료를 모두 조회합니다.
- 정렬: 상단 고정 → 최근 고정 → 선택한 작성일 정렬
- 상단 고정 공지는 `OLDEST`에서도 일반 공지보다 먼저 표시됩니다.
- 목록 조회는 조회수를 증가시키지 않습니다.

필터 조합 예시:

```http
# 전체 최신순
GET /api/lost-items?sort=NEWEST

# 보관 중 오래된순
GET /api/lost-items?status=HOLDING&sort=OLDEST

# 반환 완료 최신순
GET /api/lost-items?status=RETURNED&sort=NEWEST
```

## 상세 조회

```http
GET /api/lost-items/{id}
```

상세 API를 정상 호출할 때마다 조회수가 1 증가합니다. 현재 정책은 브라우저나 IP별 중복 제거 없이 요청 1회당 1회 집계입니다. 관리자 페이지에서 조회하는 경우에는 증가하지 않습니다.

```json
{
  "id": 7,
  "title": "학생회관 앞에서 발견된 검은색 지갑",
  "content": "8월 13일 오후 학생회관 앞에서 발견했습니다.",
  "status": "HOLDING",
  "statusLabel": "보관 중",
  "pinned": true,
  "viewCount": 13,
  "images": [
    {
      "id": 21,
      "url": "https://cdn.example.com/festa2026_lost_items/images/example.webp",
      "displayOrder": 0
    }
  ],
  "authorName": "축제 스태프",
  "createdAt": "2026-08-13T03:00:00Z",
  "updatedAt": "2026-08-13T03:10:00Z"
}
```

`createdAt`과 `updatedAt`은 UTC ISO-8601 형식입니다. 화면에서는 Asia/Seoul 시간대로 변환해 표시하면 됩니다.

존재하지 않는 공지는 `404 LOST_ITEM_NOT_FOUND`를 반환합니다.

## 관리 REST API

관리 API는 `Authorization: Bearer {accessToken}`이 필요하며 `STAFF`, `ADMIN`, `SUPER_ADMIN`만 호출할 수 있습니다. 권한이 부족하면 `403 LOST_ITEM_MANAGE_FORBIDDEN`을 반환합니다.

| Method | Path | Content-Type | 설명 |
|---|---|---|---|
| POST | `/api/lost-items` | `multipart/form-data` | 공지와 사진 등록 |
| PATCH | `/api/lost-items/{id}` | `multipart/form-data` | 내용 수정, 사진 추가·삭제 |
| PATCH | `/api/lost-items/{id}/status` | `application/json` | 반환 상태 변경 |
| PATCH | `/api/lost-items/{id}/pin` | `application/json` | 상단 고정 변경 |
| DELETE | `/api/lost-items/{id}` | 없음 | 공지와 사진 삭제 |

### 공지 등록

multipart의 `data` 파트는 JSON, `images` 파트는 이미지 파일 목록입니다. 사진이 없으면 `images`를 생략할 수 있습니다.

```bash
curl -X POST http://localhost:8888/api/lost-items \
  -H "Authorization: Bearer ACCESS_TOKEN" \
  -F 'data={"title":"검은색 지갑","content":"학생회관 앞에서 발견했습니다.","status":"HOLDING","pinned":false};type=application/json' \
  -F 'images=@wallet-front.webp;type=image/webp' \
  -F 'images=@wallet-back.webp;type=image/webp'
```

성공 시 `201 Created`와 생성된 `LostItemResponse`를 반환합니다.

### 공지 수정

`data`와 새 `images` 외에 삭제할 기존 사진 ID를 `removeImageIds`로 반복해서 전달할 수 있습니다.

```bash
curl -X PATCH http://localhost:8888/api/lost-items/7 \
  -H "Authorization: Bearer ACCESS_TOKEN" \
  -F 'data={"title":"검은색 지갑","content":"학생회관 분실물 센터에서 보관 중입니다.","status":"HOLDING","pinned":true};type=application/json' \
  -F 'removeImageIds=21' \
  -F 'images=@new-photo.webp;type=image/webp'
```

수정 후 남는 기존 사진과 새 사진을 합해 최대 5장이어야 합니다.

### 상태 및 고정 변경

```json
PATCH /api/lost-items/7/status
{"status":"RETURNED"}
```

```json
PATCH /api/lost-items/7/pin
{"pinned":true}
```

Access Token이 갱신되면 기존 API와 동일하게 `X-Access-Token` 응답 헤더가 제공되며, Refresh Token이 회전하면 `/api` 경로의 HttpOnly Cookie가 갱신됩니다.
