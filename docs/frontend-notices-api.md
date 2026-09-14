# 일반 공지 API

[문서 목차](README.md) · [프런트 연동 시작하기](frontend-getting-started.md)

분실물(`/api/lost-items`)과 별개인 일반 공지입니다. 댓글 기능은 없습니다.
인증·토큰 갱신과 오류 공통 규칙은 [공통 API 규약](frontend-api-common.md)을 따릅니다.
목록·상세는 비로그인 공개이며 작성·수정·삭제·상단 고정은 STAFF, ADMIN, SUPER_ADMIN만 가능합니다.
관리자 웹 메뉴는 `/admin/notices`입니다.

## 최상단 배너 공지

기존 등록·수정 API의 `data`에 `banner: true`를 넣으면 최상단 배너 공지로 지정합니다.
관리자 작성·수정 화면의 **최상단 고정 (배너 공지)** 체크박스로도 설정합니다.
`pinned`와 독립적인 값이며 여러 공지를 동시에 배너로 지정할 수 있습니다.
등록 시 `banner`를 생략하거나 null로 보내면 false, 수정 시 생략하거나 null로 보내면 기존 값을 유지합니다.
해제하려면 수정 API에 `banner: false`를 보냅니다. 기존 `/pin` API는 `pinned`만 변경합니다.

목록과 상세 응답에 `banner` boolean이 포함됩니다. 공개 배너 영역은
`GET /api/notices?bannerOnly=true&page=0&size=20`으로 조회합니다. 필터 적용 후 페이지네이션하며
`totalElements`와 `totalPages`도 배너 공지만 집계합니다. `bannerOnly` 기본값은 false입니다.
배너가 여러 페이지면 필요한 페이지를 추가 조회합니다. 목록 조회로 조회수는 증가하지 않습니다.

사용자 프런트는 조회한 공지 제목을 페이지 최상단 배너로 렌더링하고 클릭하면 해당 공지 상세로 이동하도록 연결합니다.
이 저장소는 배너 지정·조회 API와 관리자 설정 화면을 제공하며, 사용자 사이트의 실제 배너 UI는 프런트에서 구현해야 합니다.

| 메서드 | 경로 | 기능 |
|---|---|---|
| GET | `/api/notices?sort=NEWEST&page=0&size=20` | 목록 |
| GET | `/api/notices/{id}` | 상세, 조회수 1 증가 |
| POST | `/api/notices` | 등록 (`multipart/form-data`) |
| PATCH | `/api/notices/{id}` | 수정 (`multipart/form-data`) |
| PATCH | `/api/notices/{id}/pin` | 고정 변경 (`application/json`) |
| DELETE | `/api/notices/{id}` | 공지·첨부파일 삭제, 204 응답 |

## 조회 응답

```json
{
  "id": 1,
  "title": "축제 운영 안내",
  "content": "운영 시간을 안내합니다.",
  "pinned": true,
  "banner": true,
  "viewCount": 0,
  "media": [
    {
      "id": 9,
      "url": "https://cdn.example.com/festa2026_notices/attachments/example.png",
      "originalFilename": "축제안내.png",
      "contentType": "image/png",
      "size": 4096,
      "displayOrder": 0
    }
  ],
  "files": [
    {
      "id": 10,
      "url": "https://cdn.example.com/festa2026_notices/attachments/example.pdf",
      "originalFilename": "안내.pdf",
      "contentType": "application/pdf",
      "size": 2048,
      "displayOrder": 0
    }
  ],
  "authorName": "운영자",
  "createdAt": "2026-09-11T01:00:00Z",
  "updatedAt": "2026-09-11T01:00:00Z"
}
```

목록은 `{ "items": [상세 응답], "page": 0, "size": 20, "totalElements": 1, "totalPages": 1 }` 구조입니다.
`size`는 1–100으로 보정됩니다. `sort`는 `NEWEST` 또는 `OLDEST`입니다.
배너 글이 가장 먼저 나오고 각 그룹에서 고정 여부, 최근 고정 시각, 작성일과 ID 순으로 정렬합니다.
즉 배너 공지 → 일반 상단 고정 공지 → 일반 공지 순이며, NEWEST/OLDEST는 작성일과 ID 정렬 방향에 적용됩니다.
목록 조회와 관리자 편집 화면에서는 조회수를 증가시키지 않습니다.

응답은 두 배열로 구분됩니다. 비어 있는 그룹은 `[]`이며 기존 통합 `attachments` 응답은 제공하지 않습니다.

- `media`: `contentType`이 `image/`면 `<img>`, `video/`면 controls가 있는 `<video>`로 본문에 바로 표시합니다.
- `files`: 원본 파일명과 다운로드 버튼을 표시합니다. `url`이 다운로드 주소이며 문서·압축파일은 저장 시 `Content-Disposition: attachment`로 제공됩니다.
- `displayOrder`는 각 배열 안에서 0부터 시작합니다. 각 그룹 내 기존 순서를 유지하고 새 항목을 뒤에 추가합니다.

```javascript
// React 예시
{notice.media.map(item => item.contentType.startsWith('image/')
  ? <img key={item.id} src={item.url} alt={item.originalFilename} />
  : <video key={item.id} src={item.url} controls preload="metadata" />)}
{notice.files.map(file => <a key={file.id} href={file.url} download={file.originalFilename}>
  {file.originalFilename} 다운로드
</a>)}
```

기존 클라이언트의 업로드 `attachments` 파트는 `media`/`files`로 변경해야 합니다.
DB 첨부 테이블 구조는 동일하며 기존 저장 파일도 MIME 형식에 따라 분리해 반환됩니다.

## 작성·수정

`Authorization: Bearer ACCESS_TOKEN`이 필요합니다. 기존 API와 동일하게 토큰 갱신 시 `X-Access-Token` 응답 헤더와 Refresh Token 쿠키를 반영합니다.

- `data`: `application/json` 파트. 제목(필수, 최대 150자), 내용(필수, 최대 5,000자), `pinned`(true 또는 false), `banner`(선택).
- `media`: 본문에 표시할 이미지·영상 파일을 같은 파트 이름으로 여러 개 추가합니다.
- `files`: 다운로드할 PDF·문서·압축파일을 같은 파트 이름으로 여러 개 추가합니다.
- 두 파트 모두 선택 사항이며 파일 없는 글도 가능합니다. 잘못된 그룹의 파일은 `NOTICE_ATTACHMENT_GROUP_MISMATCH`(400)로 거부합니다.
- 수정 시 `removeAttachmentIds`: 삭제할 기존 미디어 또는 파일 ID를 같은 필드 이름으로 반복 전송합니다. 해당 글에 속하지 않은 ID는 400입니다.
- 수정은 제목·내용·고정 여부를 모두 보내는 방식입니다. 유지할 기존 첨부는 자동 보존되고 새 첨부는 뒤에 추가됩니다.
- 제목·내용은 일반 텍스트입니다. HTML로 삽입하지 말고 텍스트로 렌더링하고 내용의 줄바꿈을 보존하세요.

```javascript
const form = new FormData();
form.append('data', new Blob([JSON.stringify({
  title: '축제 운영 안내', content: '운영 시간을 안내합니다.\n첨부 문서를 확인해 주세요.', pinned: true, banner: true
})], { type: 'application/json' }));
for (const file of selectedMedia) form.append('media', file);
for (const file of selectedFiles) form.append('files', file);
// 수정 요청에서만: for (const id of removedIds) form.append('removeAttachmentIds', String(id));
const response = await fetch('/api/notices', {
  method: 'POST', headers: { Authorization: `Bearer ${accessToken}` },
  credentials: 'include', body: form
});
// multipart Content-Type은 브라우저가 boundary와 함께 설정하도록 둡니다.
```

등록은 201, 수정·고정 변경은 200으로 위 상세 응답을 반환합니다.
고정만 바꿀 때는 `/api/notices/{id}/pin`에 `{"pinned": true}`를 PATCH합니다.

`pinned`는 primitive boolean으로 생략 시 false가 적용됩니다. 수정 화면에서 고정을 유지하려면 현재 값을 반드시 전송합니다. `banner`의 생략/null 유지 규칙과 혼동하지 않습니다. 첨부 삭제·추가 후 남는 전체 개수가 10개 이하여야 하며 그룹별 10개가 아닙니다.

## 첨부 제한 (기본값)

글당 기존 파일과 새 파일을 합쳐 최대 10개입니다.

| 종류 | 지원 확장자 | 파일당 용량 |
|---|---|---|
| 사진 | jpg, jpeg, png, webp, gif | 10MB |
| 영상 | mp4, webm, mov | 200MB |
| 문서·압축 | pdf, txt, doc, docx, xls, xlsx, ppt, pptx, hwp, hwpx, zip | 20MB |

전체 multipart 요청은 기본 650MB까지 가능합니다. 브라우저별 동영상 코덱 지원은 다를 수 있습니다.
사진·영상 용량은 기존 `r2.image-max-size`, `r2.video-max-size`, 문서는 `notice.document-max-size`를 사용합니다.
파일 형식은 확장자 허용 목록으로 제한하고 저장 MIME을 지정합니다. 파일 내용 변환이나 악성코드 검사는 수행하지 않습니다.

주요 오류는 `NOTICE_MANAGE_FORBIDDEN`(403), `NOTICE_NOT_FOUND`(404), `INVALID_NOTICE`(400),
`INVALID_NOTICE_ATTACHMENT_ID`(400), `NOTICE_ATTACHMENT_LIMIT_EXCEEDED`(400),
`UNSUPPORTED_ATTACHMENT_TYPE`(400), `ATTACHMENT_FILE_TOO_LARGE`(400), `MEDIA_UPLOAD_FAILED`(503)입니다.
인증 전 multipart 요청은 기존 업로드 인증 필터에서도 거부됩니다.

## 저장 및 배포

신규 테이블은 `notices`, `notice_attachments`입니다. 현재 기본 `spring.jpa.hibernate.ddl-auto=update`에서는
애플리케이션 시작 시 생성됩니다. 수동 DDL 환경은 [공지 스키마](notices-schema.sql)를 먼저 적용합니다.
기존 테이블에서 수동 DDL을 사용하면 [배너 컬럼 추가 SQL](notices-banner-migration.sql)을 한 번 적용합니다.
`notices.banner`는 NOT NULL, 기본값 false이므로 기존 공지는 배너로 지정되지 않습니다.
기존 R2 버킷·공개 URL·인증정보를 사용하며 기본 키 접두사는 `festa2026_notices`입니다.
DB 롤백 시 이번 요청에서 업로드한 파일을 정리하고, 첨부 교체·공지 삭제는 DB 커밋 후 기존 파일을 삭제합니다.

## 작성자 표시와 탈퇴 범위

`authorName`은 작성 당시 이름을 공지 DB에 저장한 값입니다. 조회할 때 SSO에서 다시 가져오지 않으며, 수정자의 이름으로 바뀌지도 않습니다. 현재 `FestivalWithdrawalService`는 일반 공지를 익명화하지 않으므로 SSO 탈퇴와 축제 서비스 이용 정보 삭제 모두 이 공지의 작성자 이름·UUID를 자동 제거하지 않습니다. [탈퇴 범위와 현재 제약](frontend-user-api.md)을 함께 확인합니다.
