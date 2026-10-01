# 부스 지도·찜 API

[문서 목차](README.md) · [프런트 연동 시작하기](frontend-getting-started.md)

**이 문서의 순서**

- [핵심 정책](#핵심-정책)
- [프런트 타입](#프런트-타입)
- [카테고리와 지도 핀](#카테고리와-지도-핀)
- [전체 부스 핀 조회](#전체-부스-핀-조회)
- [부스 상세 조회](#부스-상세-조회)
- [찜](#찜)
- [ADMIN 이상 관리 API](#admin-이상-관리-api)
- [주요 오류](#주요-오류)

공통 인증, `X-Access-Token`, 오류 처리는 [공통 API 규약](frontend-api-common.md)을 따릅니다.

## 핵심 정책

- 좌표는 WGS84 위도·경도입니다.
- 운영시간은 날짜·시간대가 없는 `HH:mm:ss` 형식입니다. API에는 별도의 운영 날짜·시즌 필드가 없습니다.
- 전체·상세 조회는 공개 API입니다.
- 전체 목록·내 찜 목록·관리자 목록은 일반부스(`GENERAL`) → 외부부스(`EXTERNAL`) → 학생회(`STUDENT_COUNCIL`) → 교내기관(`CAMPUS`) → 포토부스(`PHOTO_BOOTH`) → 푸드트럭(`FOOD_TRUCK`) → 기타(`OTHER`) 순서입니다. 같은 카테고리 안에서는 기존 부스명 오름차순을 유지합니다.
- 로그인 상태에서 Bearer Token을 선택적으로 보내면 `favorited`가 현재 사용자 기준으로 계산됩니다.
- 목록·상세·내 찜 목록의 `favoriteCount`는 해당 부스를 찜한 전체 사용자 수입니다. 찜이 없으면 `0`입니다.
- `representativeMedia`는 미디어가 없으면 `null`입니다.

## 프런트 타입

```ts
type BoothCategory = "PHOTO_BOOTH" | "GENERAL" | "FOOD_TRUCK" | "STUDENT_COUNCIL" | "CAMPUS" | "EXTERNAL" | "OTHER";

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
  description: string;
  category: BoothCategory;
  opensAt: string;
  closesAt: string;
  stampEnabled: boolean;
  representativeMedia: BoothMedia | null;
  favorited: boolean;
  favoriteCount: number;
};

type BoothDetail = BoothSummary & {
  media: BoothMedia[];
  createdAt: string;
  updatedAt: string;
};
```

## 카테고리와 지도 핀

| `category` | 의미 | 지도 핀 |
|---|---|---|
| `PHOTO_BOOTH` | 포토부스 | 포토부스 |
| `GENERAL` | 일반부스(동아리 등) | 일반부스 |
| `FOOD_TRUCK` | 푸드트럭 | 푸드트럭 |
| `STUDENT_COUNCIL` | 학생회·팔찌배부존 | 학생회·팔찌배부존 |
| `CAMPUS` | 교내 기관·부서 운영 부스 | 일반부스 |
| `EXTERNAL` | 외부 부스 | 일반부스 |
| `OTHER` | 기타 | 일반부스 |

목록·상세·내 찜 목록에 `category`가 포함됩니다. 전체 탭은 카테고리 조건 없이, 북마크 탭은 `favorited`로, 나머지 탭은 `category`로 프론트에서 필터링합니다. 목록 API에 카테고리 쿼리 파라미터는 없습니다. 내 위치는 별도 마커이며 부스 카테고리에 포함하지 않습니다.

기존 부스는 스키마 변경 시 `GENERAL`로 초기화되므로 관리자가 실제 분류에 맞게 수정해야 합니다. `ddl-auto=update` 환경에서는 컬럼이 자동 추가되며, 수동 변경 환경에서는 [카테고리 스키마](booth-category-schema.sql)를 먼저 적용합니다.

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
    "description": "체험 설명",
    "category": "GENERAL",
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
    "favoriteCount": 0,
    "favorited": false
  }
]
```

지도 핀은 `latitude`, `longitude`를 사용하고, 목록 카드와 핀 팝업의 설명은 `description`을 사용하면 됩니다. `operator`는 운영 주체입니다. 목록과 내 찜 목록 모두 상세 조회와 동일한 설명 전문을 반환합니다. 초기 화면에서 이 배열 전체를 지도에 표시합니다.

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
  "category": "GENERAL",
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
  "favoriteCount": 0,
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

### 등록·수정 입력 제한

POST와 PATCH는 같은 전체 입력 DTO를 사용합니다. PATCH에 변경할 필드만 보내면 필수값 검증에 실패합니다.

| 필드 | 규칙 |
|---|---|
| `latitude`, `longitude` | 필수 숫자, 각각 -90–90 / -180–180 |
| `name`, `operator` | 필수, 각각 최대 150자, 공백만 입력 불가 |
| `category` | 필수, 위 enum 문자열 중 하나; PATCH에서도 현재 값 또는 변경할 값 전송 |
| `description` | 필수, 최대 5000자, 공백만 입력 불가 |
| `opensAt`, `closesAt` | 필수 `LocalTime`, 종료가 시작보다 늦어야 함; 자정을 넘는 범위 불가 |
| `stampEnabled` | boolean, 생략하면 false; 유지하려면 현재 값 전송 |
| `managerUuids` | 최대 100개 UUID, null 항목 불가; 생략/null/빈 배열이면 담당자 전체 해제 |

담당자는 축제 서비스에 연결된 사용자만 지정할 수 있고 중복 UUID는 하나로 처리합니다. 존재하지 않는 UUID가 섞이면 `400 BOOTH_MANAGER_NOT_FOUND`입니다. 저장 시 담당자 관계와 `BOOTH_MANAGER` 역할을 조정하며, 다른 부스를 담당하는 사용자는 역할이 유지됩니다.

미디어 개수는 새 파일만이 아니라 기존 파일과 합산합니다(이미지 5개, 동영상 3개). 파일 크기·MIME은 [업로드 규약](api-upload-limits.md)을 참고합니다. 대표 미디어가 없을 때 첫 미디어를 자동 지정하며 대표를 삭제하면 남은 첫 미디어를 지정합니다. 미디어가 없으면 대표값은 null입니다.

### 부스 등록·수정 본문

```json
{
  "latitude": 37.6432,
  "longitude": 127.1059,
  "name": "멋사 체험 부스",
  "operator": "멋쟁이사자처럼",
  "category": "GENERAL",
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
| `400` | `EMPTY_BOOTH_MEDIA_FILE`, `UNSUPPORTED_BOOTH_MEDIA_TYPE`, `BOOTH_MEDIA_FILE_TOO_LARGE` | 빈 파일·지원하지 않는 형식·개별 파일 크기 제한 확인 |
| `413` | `UPLOAD_TOO_LARGE` | 파일 크기 안내 |
| `503` | `BOOTH_MEDIA_UPLOAD_FAILED` | 저장소 업로드 실패. 상태 확인 후 사용자가 재시도 |

관리자 HTML 화면은 `/admin/booths`입니다.
