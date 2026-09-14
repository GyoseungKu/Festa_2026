# 공연 API

[문서 목차](README.md) · [프런트 연동 시작하기](frontend-getting-started.md)

공연 조회와 관리는 모두 Bearer 인증이 필요합니다. 공개 시각(`publishedAt`)이 지난 공연만 사용자 조회 API에 나타납니다.

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
- 공개 전 공연과 존재하지 않는 공연은 상세 조회에서 `404 PERFORMANCE_NOT_FOUND`로 동일하게 처리됩니다.
- 목록 응답은 `Performance[]`, 상세 응답은 `Performance`입니다.

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

- `memberNames`: 1~100개, 항목 최대 100자
- `teamName`: 최대 150자
- `description`: 최대 5000자
- 일반 `links`: 최대 3개
- 이미지와 동영상: 종류별로 링크와 업로드 파일을 합해 최대 3개
- 링크는 `http` 또는 `https` URL만 허용
- 파일 업로드 FormData key는 `files`

기본 정보 수정 시 기존 업로드 파일은 유지됩니다. `imageUrls`, `videoUrls`는 링크 소스 구성을 새 요청 값으로 교체하므로 현재 유지할 링크도 다시 보내야 합니다.

PATCH도 `category`, `teamName`, `memberNames`, `startsAt`, `endsAt`, `description`, `publishedAt`이 모두 필수입니다. 종료 시각은 시작보다 늦어야 합니다. `links`, `imageUrls`, `videoUrls`의 생략/null은 빈 목록으로 처리하므로 링크 유지 시 전체 목록을 다시 보냅니다. URL 하나의 최대 길이는 2048자입니다.

업로드의 MIME·파일별 크기·전체 요청 제한은 [업로드 규약](api-upload-limits.md)을 참고합니다. 미공개 공연을 관리자가 조회하는 REST 전용 목록은 없으며, 사용자 GET은 관리자 토큰이어도 공개 시각 필터를 적용합니다. 미공개 목록·편집은 `/admin/performances` 웹 화면에서 제공합니다.

## 주요 오류

| HTTP | code | 의미 |
|---|---|---|
| `400` | `INVALID_PERFORMANCE`, `INVALID_PERFORMANCE_TIME` | 필드 또는 시간 범위 오류 |
| `400` | `INVALID_MEDIA_URL`, `TOO_MANY_MEDIA` | 링크 또는 개수 오류 |
| `400` | `UNSUPPORTED_MEDIA_TYPE`, `MEDIA_FILE_TOO_LARGE` | 지원하지 않는 형식 또는 개별 파일 제한 초과 |
| `403` | `PERFORMANCE_MANAGE_FORBIDDEN` | 관리자 권한 없음 |
| `404` | `PERFORMANCE_NOT_FOUND` | 공연 없음 또는 미공개 |
| `413` | `UPLOAD_TOO_LARGE` | multipart 요청 전체 크기 초과 |
