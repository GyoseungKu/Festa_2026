# 부스 지도·찜 API

공통 인증, `X-Access-Token`, 오류 처리는 [공통 API 규약](frontend-api-common.md)을 따릅니다.

## 등록·수정 입력 제한

POST와 PATCH는 같은 전체 입력 DTO를 사용합니다. PATCH에 변경할 필드만 보내면 필수값 검증에 실패합니다.

| 필드 | 규칙 |
|---|---|
| `latitude`, `longitude` | 필수 숫자, 각각 -90~90 / -180~180 |
| `name`, `operator` | 필수, 각각 최대 150자, 공백만 입력 불가 |
| `description` | 필수, 최대 5000자, 공백만 입력 불가 |
| `opensAt`, `closesAt` | 필수 `LocalTime`, 종료가 시작보다 늦어야 함; 자정을 넘는 범위 불가 |
| `stampEnabled` | boolean, 생략하면 false; 유지하려면 현재 값 전송 |
| `managerUuids` | 최대 100개 UUID, null 항목 불가; 생략/null/빈 배열이면 담당자 전체 해제 |

담당자는 축제 서비스에 연결된 사용자만 지정할 수 있고 중복 UUID는 하나로 처리합니다. 존재하지 않는 UUID가 섞이면 `400 BOOTH_MANAGER_NOT_FOUND`입니다. 저장 시 담당자 관계와 `BOOTH_MANAGER` 역할을 조정하며, 다른 부스를 담당하는 사용자는 역할이 유지됩니다.

미디어 개수는 새 파일만이 아니라 기존 파일과 합산합니다(이미지 5개, 동영상 3개). 파일 크기·MIME은 [업로드 규약](api-upload-limits.md)을 참고합니다. 대표 미디어가 없을 때 첫 미디어를 자동 지정하며 대표를 삭제하면 남은 첫 미디어를 지정합니다. 미디어가 없으면 대표값은 null입니다.

## 핵심 정책

- 좌표는 WGS84 위도·경도입니다.
- 운영시간은 날짜·시간대가 없는 `HH:mm:ss` 형식입니다. API에는 별도의 운영 날짜·시즌 필드가 없습니다.
- 전체·상세 조회는 공개 API입니다.
- 로그인 상태에서 Bearer Token을 선택적으로 보내면 `favorited`가 현재 사용자 기준으로 계산됩니다.
- 전체 찜 수는 공개하지 않습니다.
- `representativeMedia`는 미디어가 없으면 `null`입니다.

## 프런트 타입

```ts
type BoothMediaKind = "IMAGE" | "VIDEO";

type BoothMedia = {
  id: number;
  kind: BoothMediaKind;
  url: string;
  displayOrder: number;
  representative: boolean;
};

type BoothSummary = {
  id: number;
  latitude: number;
  longitude: number;
  name: string;
  operator: string;
  opensAt: string;
  closesAt: string;
  stampEnabled: boolean;
  representativeMedia: BoothMedia | null;
  favorited: boolean;
};

type BoothDetail = BoothSummary & {
  description: string;
  media: BoothMedia[];
  createdAt: string;
  updatedAt: string;
};
```

## 전체 부스 핀 조회

```http
GET /api/booths
Authorization: Bearer ACCESS_TOKEN  # 선택
```

성공 `200`은 `BoothSummary[]`입니다.

```json
[
  {
    "id": 7,
    "latitude": 37.6432,
    "longitude": 127.1059,
    "name": "멋사 체험 부스",
    "operator": "멋쟁이사자처럼",
    "opensAt": "10:00:00",
    "closesAt": "18:00:00",
    "stampEnabled": true,
    "representativeMedia": {
      "id": 12,
      "kind": "IMAGE",
      "url": "https://cdn.example.com/booth.webp",
      "displayOrder": 0,
      "representative": true
    },
    "favorited": false
  }
]
```

지도 핀은 `latitude`, `longitude`를 사용하고, 핀 팝업은 `name`, `operator`, `representativeMedia`를 사용하면 됩니다. 초기 화면에서 이 배열 전체를 지도에 표시합니다.

## 부스 상세 조회

```http
GET /api/booths/{boothId}
Authorization: Bearer ACCESS_TOKEN  # 선택
```

성공 `200`은 `BoothDetail`입니다. `media` 배열 자체가 이미지·동영상 통합 노출 순서이며 `displayOrder` 오름차순입니다. 종류별로 다시 정렬하지 마십시오.

```json
{
  "id": 7,
  "latitude": 37.6432,
  "longitude": 127.1059,
  "name": "멋사 체험 부스",
  "operator": "멋쟁이사자처럼",
  "description": "체험 설명",
  "opensAt": "10:00:00",
  "closesAt": "18:00:00",
  "stampEnabled": true,
  "media": [
    {
      "id": 12,
      "kind": "IMAGE",
      "url": "https://cdn.example.com/booth.webp",
      "displayOrder": 0,
      "representative": true
    },
    {
      "id": 13,
      "kind": "VIDEO",
      "url": "https://cdn.example.com/booth.mp4",
      "displayOrder": 1,
      "representative": false
    }
  ],
  "representativeMedia": {
    "id": 12,
    "kind": "IMAGE",
    "url": "https://cdn.example.com/booth.webp",
    "displayOrder": 0,
    "representative": true
  },
  "favorited": false,
  "createdAt": "2026-08-18T01:00:00Z",
  "updatedAt": "2026-08-18T02:00:00Z"
}
```

## 찜

Bearer 인증이 필요합니다.

| Method | Path | 성공 응답 |
|---|---|---|
| `GET` | `/api/users/me/favorite-booths` | `200`, `BoothSummary[]` |
| `POST` | `/api/booths/{boothId}/favorite` | `204` |
| `DELETE` | `/api/booths/{boothId}/favorite` | `204` |

등록과 해제는 멱등입니다. 낙관적으로 하트를 바꿀 수 있지만 실패하면 이전 값으로 복원합니다.

```ts
async function setFavorite(boothId: number, favorite: boolean) {
  await apiFetch<void>(`/api/booths/${boothId}/favorite`, {
    method: favorite ? "POST" : "DELETE",
    auth: true,
  });
}
```

## ADMIN 이상 관리 API

### 부스 등록·수정 본문

```json
{
  "latitude": 37.6432,
  "longitude": 127.1059,
  "name": "멋사 체험 부스",
  "operator": "멋쟁이사자처럼",
  "description": "체험 설명",
  "opensAt": "10:00:00",
  "closesAt": "18:00:00",
  "stampEnabled": true,
  "managerUuids": ["123e4567-e89b-12d3-a456-426614174000"]
}
```

| Method | Path | 성공 |
|---|---|---|
| `POST` | `/api/booths` | `201`, `BoothAdminResponse` |
| `PATCH` | `/api/booths/{boothId}` | `200`, `BoothAdminResponse` |
| `POST` | `/api/booths/{boothId}/images` | `200`, `BoothAdminResponse` |
| `POST` | `/api/booths/{boothId}/videos` | `200`, `BoothAdminResponse` |
| `PATCH` | `/api/booths/{boothId}/media/order` | `200`, `BoothAdminResponse` |
| `DELETE` | `/api/booths/{boothId}/media/{mediaId}` | `200`, `BoothAdminResponse` |
| `DELETE` | `/api/booths/{boothId}` | `204` |

`BoothAdminResponse`는 `{ "booth": BoothDetail, "managerUuids": string[] }` 형태입니다.

파일 업로드는 `FormData`의 `files` key를 반복해서 사용합니다. 이미지 최대 5개, 동영상 최대 3개입니다.

```ts
const form = new FormData();
files.forEach((file) => form.append("files", file));
await apiFetch(`/api/booths/${boothId}/images`, {
  method: "POST",
  auth: true,
  body: form,
});
```

통합 순서와 대표 항목 변경:

```json
{
  "mediaIds": [12, 9, 15],
  "representativeMediaId": 9
}
```

`mediaIds`에는 현재 부스의 모든 미디어 ID를 누락·중복 없이 보내야 합니다. 이미지와 동영상을 섞어 원하는 표시 순서로 보냅니다. 대표 항목은 이미지 또는 동영상 모두 가능합니다.

## 주요 오류

| HTTP | code | 처리 |
|---|---|---|
| `401` | `UNAUTHORIZED` | 로그인 상태 제거 |
| `403` | `BOOTH_MANAGE_FORBIDDEN` | 관리 UI 접근 차단 |
| `404` | `BOOTH_NOT_FOUND`, `BOOTH_MEDIA_NOT_FOUND` | 목록 새로고침 |
| `400` | `INVALID_BOOTH_COORDINATES`, `INVALID_BOOTH_HOURS` | 좌표·운영시간 필드 표시 |
| `400` | `INVALID_BOOTH_MEDIA_ORDER` | 서버 상세를 다시 조회해 미디어 목록 동기화 |
| `400` | `TOO_MANY_BOOTH_MEDIA` | 파일 개수 안내 |
| `413` | `UPLOAD_TOO_LARGE` | 파일 크기 안내 |

관리자 HTML 화면은 `/admin/booths`입니다.
