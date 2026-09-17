# 분실물 공지 API

[문서 목차](README.md) · [프런트 연동 시작하기](frontend-getting-started.md)

공통 오류와 토큰 갱신 처리는 [공통 API 규약](frontend-api-common.md)을 따릅니다.

## 타입

```ts
type LostItemStatus = "HOLDING" | "RETURNED";
type LostItemSort = "NEWEST" | "OLDEST";

type LostItem = {
  id: number;
  title: string;
  content: string;
  foundLocation: string | null; // 발견장소, 미입력/기존 게시물은 null
  status: LostItemStatus;
  statusLabel: string;
  pinned: boolean;
  viewCount: number;
  images: Array<{ id: number; url: string; displayOrder: number }>;
  authorName: string;
  createdAt: string;
  updatedAt: string;
};

type LostItemPage = {
  items: LostItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};
```

## 공개 목록

```http
GET /api/lost-items?status=HOLDING&sort=NEWEST&page=0&size=20
```

| 쿼리 | 값 |
|---|---|
| `status` | 선택, `HOLDING` 또는 `RETURNED`; 생략 시 전체 |
| `sort` | `NEWEST` 기본, `OLDEST` |
| `page` | 0부터 시작, 기본 0 |
| `size` | 기본 20, 최대 100 |

상단 고정 공지가 항상 먼저 나오고, 그 안에서 최근 고정순, 이후 선택한 작성일순으로 정렬됩니다. 목록 조회는 조회수를 증가시키지 않습니다.

성공 `200`은 `LostItemPage`입니다.

## 공개 상세

```http
GET /api/lost-items/{id}
```

성공할 때마다 조회수가 1 증가합니다. 브라우저나 IP 중복 제거는 없습니다. `createdAt`, `updatedAt`은 UTC ISO-8601이며 화면에서 `Asia/Seoul`로 변환합니다.

```json
{
  "id": 7,
  "title": "학생회관 앞에서 발견된 검은색 지갑",
  "content": "학생회관 분실물 센터에서 보관 중입니다.",
  "foundLocation": "학생회관 1층 입구",
  "status": "HOLDING",
  "statusLabel": "보관 중",
  "pinned": true,
  "viewCount": 13,
  "images": [
    { "id": 21, "url": "https://cdn.example.com/wallet.webp", "displayOrder": 0 }
  ],
  "authorName": "축제 스태프",
  "createdAt": "2026-08-18T03:00:00Z",
  "updatedAt": "2026-08-18T03:10:00Z"
}
```

## STAFF 이상 관리 API

| Method | Path | Content-Type | 성공 |
|---|---|---|---|
| `POST` | `/api/lost-items` | multipart | `201`, 생성된 `LostItem` |
| `PATCH` | `/api/lost-items/{id}` | multipart | `200`, 수정된 `LostItem` |
| `PATCH` | `/api/lost-items/{id}/status` | JSON | `200`, 수정된 `LostItem` |
| `PATCH` | `/api/lost-items/{id}/pin` | JSON | `200`, 수정된 `LostItem` |
| `DELETE` | `/api/lost-items/{id}` | 없음 | `204` |

공지 JSON:

```ts
type LostItemMutation = {
  title: string;      // 1~150자
  content: string;    // 1~5000자
  foundLocation?: string | null; // 선택, 최대 200자. 공백/생략/null은 미입력
  status: LostItemStatus;
  pinned: boolean;
};
```

사진은 최대 5개이며 JPG, PNG, WebP를 지원합니다.

PATCH도 `title`, `content`, `status`가 필수이고 `pinned`를 생략하면 false가 됩니다. `data`에는 유지할 값도 포함합니다. 파일 크기·인증 필터·전체 요청 제한은 [업로드 규약](api-upload-limits.md)을 참고합니다.

`authorName`은 작성 당시 저장한 이름입니다. SSO 프로필 변경·탈퇴로 자동 갱신되지 않으며, 축제 서비스 이용 정보 삭제에서는 별도로 `알 수 없음`으로 익명화됩니다.

발견장소는 공지 JSON의 `foundLocation`으로 전달합니다(별도 multipart 파트가 아님). 목록·상세·관리 응답에 포함됩니다. 수정 시 생략하거나 빈 값으로 보내면 기존 발견장소를 지웁니다. 내용에 적힌 기존 장소는 자동 추출하지 않습니다. DB에는 `lost_item_notices.found_location`(nullable, 200자)을 추가합니다.

### 등록 FormData

```ts
const form = new FormData();
form.append("data", new Blob([JSON.stringify(payload)], { type: "application/json" }));
images.forEach((image) => form.append("images", image));

await apiFetch<LostItem>("/api/lost-items", {
  method: "POST",
  auth: true,
  body: form,
});
```

### 수정 FormData

기존 사진 삭제 ID는 `removeImageIds`를 반복해서 추가합니다. 삭제 후 남는 사진과 새 사진 합계가 5개 이하여야 합니다.

```ts
const form = new FormData();
form.append("data", new Blob([JSON.stringify(payload)], { type: "application/json" }));
removeImageIds.forEach((id) => form.append("removeImageIds", String(id)));
newImages.forEach((image) => form.append("images", image));

await apiFetch<LostItem>(`/api/lost-items/${id}`, {
  method: "PATCH",
  auth: true,
  body: form,
});
```

### 상태·고정 변경

```http
PATCH /api/lost-items/7/status
Content-Type: application/json

{ "status": "RETURNED" }
```

```http
PATCH /api/lost-items/7/pin
Content-Type: application/json

{ "pinned": true }
```

## 주요 오류

| HTTP | code | 처리 |
|---|---|---|
| `400` | `INVALID_LOST_ITEM` | 제목·내용·상태 확인 |
| `400` | `INVALID_LOST_ITEM_IMAGE_ID` | 상세를 다시 조회해 사진 동기화 |
| `400` | `LOST_ITEM_IMAGE_LIMIT_EXCEEDED` | 최대 5장 안내 |
| `400` | `UNSUPPORTED_IMAGE_TYPE` | JPG·PNG·WebP 파일인지 확인 |
| `400` | `EMPTY_IMAGE_FILE` | 빈 사진 파일 제외 |
| `400` | `IMAGE_FILE_TOO_LARGE` | 파일당 이미지 용량 제한 확인 (기본 10MB) |
| `403` | `LOST_ITEM_MANAGE_FORBIDDEN` | 관리 UI 접근 차단 |
| `404` | `LOST_ITEM_NOT_FOUND` | 목록으로 이동 |
| `413` | `UPLOAD_TOO_LARGE` | 파일 크기 안내 |
| `415` | `UNSUPPORTED_MEDIA_TYPE` | multipart 및 이미지 형식 확인 |

관리자 HTML 화면은 `/admin/lost-items`입니다.
