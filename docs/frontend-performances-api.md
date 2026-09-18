# 공연 API

[문서 목차](README.md) · [프런트 연동 시작하기](frontend-getting-started.md)

공연 조회와 관리는 모두 Bearer 인증이 필요합니다. 공개 시각(`publishedAt`) 전에도 공연은 조회되며, 식별 정보는 TBA로 숨깁니다. 공개 시각부터 실제 정보를 반환합니다.

## 타입

```ts
type PerformanceCategory = "CELEBRITY" | "CLUB" | "INDIVIDUAL";
type PerformanceMediaKind = "IMAGE" | "VIDEO";
type PerformanceMediaSource = "LINK" | "UPLOAD";

type PerformanceMedia = {
  id: number;
  kind: PerformanceMediaKind;
  source: PerformanceMediaSource;
  url: string;
};

type Performance = {
  id: number;
  category: PerformanceCategory;
  categoryLabel: string;
  teamName: string;
  memberNames: string[];
  startsAt: string;
  endsAt: string;
  description: string;
  links: string[];
  images: PerformanceMedia[];
  videos: PerformanceMedia[];
  publishedAt: string;
  published: boolean;
  createdAt: string;
  updatedAt: string;
};
```

모든 시각은 UTC ISO-8601 `Instant`입니다. 화면에서는 `Asia/Seoul`로 변환합니다.

## 사용자 조회

```http
GET /api/performances
GET /api/performances/{id}
Authorization: Bearer ACCESS_TOKEN
```

- 목록은 공연 시작 시각 오름차순입니다.
- 공개 전 공연도 목록에 포함되며 상세 조회는 `200`입니다. 존재하지 않는 공연만 `404 PERFORMANCE_NOT_FOUND`입니다.
- 목록 응답은 `Performance[]`, 상세 응답은 `Performance`입니다.

| 공개 전 필드 | 값 |
|---|---|
| `teamName` | `"TBA"` |
| `description` | `""` |
| `memberNames`, `links`, `images`, `videos` | `[]` |
| `published` | `false` |
| `id`, `category`, `categoryLabel`, 시작·종료·공개·생성·수정 시각 | 원래 값 유지 |

프런트는 `published=false`이면 TBA 카드와 기본 이미지를 표시합니다. 응답에서 미공개 항목을 제외하지 마세요. 공개 시각 이후 다시 조회하면 같은 ID에 실제 정보가 반환됩니다. 응답 필드 타입은 기존과 동일하며, 공개 시각과 현재 서버 시각이 같으면 공개 상태입니다.

## ADMIN 이상 관리

### 등록·수정 본문

```json
{
  "category": "CLUB",
  "teamName": "천보 밴드",
  "memberNames": ["김학생", "이학생"],
  "startsAt": "2026-10-06T09:00:00Z",
  "endsAt": "2026-10-06T09:30:00Z",
  "description": "공연 소개",
  "links": ["https://example.com/profile"],
  "imageUrls": ["https://example.com/poster.webp"],
  "videoUrls": [],
  "publishedAt": "2026-09-01T00:00:00Z"
}
```

| Method | Path | 성공 |
|---|---|---|
| `POST` | `/api/performances` | `201`, 생성된 `Performance` |
| `PATCH` | `/api/performances/{id}` | `200`, 수정된 `Performance` |
| `POST` | `/api/performances/{id}/images` | `200`, 수정된 `Performance` |
| `POST` | `/api/performances/{id}/videos` | `200`, 수정된 `Performance` |
| `DELETE` | `/api/performances/{id}/media/{mediaId}` | `200`, 수정된 `Performance` |
| `DELETE` | `/api/performances/{id}` | `204` |

- `memberNames`: 1–100개, 항목 최대 100자
- `teamName`: 최대 150자
- `description`: 최대 5000자
- DB의 `festival_performances.description`은 `TEXT`로 저장합니다. 기존 DB에서 `Data too long for column 'description'`이 발생하면 [컬럼 변경 SQL](performance-description-migration.sql)로 현재 타입을 확인하고 작은 컬럼을 확장해야 합니다. API 입력 제한은 그대로 유지됩니다.
- 일반 `links`: 최대 3개
- 이미지와 동영상: 종류별로 링크와 업로드 파일을 합해 최대 3개
- 링크는 `http` 또는 `https` URL만 허용
- 파일 업로드 FormData key는 `files`

기본 정보 수정 시 기존 업로드 파일은 유지됩니다. `imageUrls`, `videoUrls`는 링크 소스 구성을 새 요청 값으로 교체하므로 현재 유지할 링크도 다시 보내야 합니다.

PATCH도 `category`, `teamName`, `memberNames`, `startsAt`, `endsAt`, `description`, `publishedAt`이 모두 필수입니다. 종료 시각은 시작보다 늦어야 합니다. `links`, `imageUrls`, `videoUrls`의 생략/null은 빈 목록으로 처리하므로 링크 유지 시 전체 목록을 다시 보냅니다. URL 하나의 최대 길이는 2048자입니다.

업로드의 MIME·파일별 크기·전체 요청 제한은 [업로드 규약](api-upload-limits.md)을 참고합니다. 미공개 원문을 조회하는 REST 전용 목록은 없으며, 사용자 GET은 관리자 토큰이어도 TBA 마스킹을 적용합니다. ADMIN·SUPER_ADMIN은 `/admin/performances` 웹 화면에서 미공개 원문을 조회·편집합니다. 관리자 등록·수정 응답도 기존처럼 원문을 반환합니다.

## 주요 오류

| HTTP | code | 의미 |
|---|---|---|
| `400` | `INVALID_PERFORMANCE`, `INVALID_PERFORMANCE_TIME` | 필드 또는 시간 범위 오류 |
| `400` | `INVALID_MEDIA_URL`, `TOO_MANY_MEDIA` | 링크 또는 개수 오류 |
| `400` | `MEMBER_REQUIRED`, `TOO_MANY_LINKS` | 정규화 후 구성원이 없거나 일반 링크가 3개 초과 |
| `400` | `MEDIA_FILE_REQUIRED`, `EMPTY_MEDIA_FILE`, `INVALID_MEDIA_ID` | 업로드 파일 누락·빈 파일 또는 잘못된 삭제 ID |
| `400` | `UNSUPPORTED_MEDIA_TYPE`, `MEDIA_FILE_TOO_LARGE` | 지원하지 않는 형식 또는 개별 파일 제한 초과 |
| `403` | `PERFORMANCE_MANAGE_FORBIDDEN` | 관리자 권한 없음 |
| `404` | `PERFORMANCE_NOT_FOUND` | 공연 없음 |
| `413` | `UPLOAD_TOO_LARGE` | multipart 요청 전체 크기 초과 |
| `503` | `MEDIA_UPLOAD_FAILED` | 저장소 업로드 실패. 파일 요청을 무조건 자동 재시도하지 않음 |
