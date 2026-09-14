# 협찬사 API

관리자 페이지는 `/admin/sponsors`입니다. ADMIN/SUPER_ADMIN만 등록·수정·삭제할 수 있습니다. 조회는 로그인 없이 가능합니다.

| 메서드 | 경로 | 응답 |
|---|---|---|
| GET | `/api/sponsors` | 200, 전체 목록 배열(ID 오름차순) |
| GET | `/api/sponsors/{id}` | 200, 상세 객체 |
| POST | `/api/sponsors` | 201, 생성된 객체 |
| PUT | `/api/sponsors/{id}` | 200, 수정된 객체 |
| DELETE | `/api/sponsors/{id}` | 204, 본문 없음 |

관리 API는 `Authorization: Bearer <accessToken>`이 필요합니다. 토큰 갱신·오류 형식은 [공통 규약](frontend-api-common.md)을 참고하세요.

## 응답 객체

```json
{
  "id": 1,
  "name": "협찬사 이름",
  "description": "협찬사 설명",
  "imageUrl": "https://media.example.com/festa2026_sponsors/example.png",
  "boothId": 10,
  "boothName": "체험 부스"
}
```

연결 부스가 없으면 `boothId`, `boothName`은 null입니다. 설명은 HTML이 아닌 일반 텍스트로 렌더링하세요.

## 등록·수정

JSON 대신 `multipart/form-data`를 보냅니다.

| 필드 | 규칙 |
|---|---|
| name | 필수, 공백 제거 후 1~100자 |
| description | 필수, 공백 제거 후 1~2000자 |
| image | 등록 필수/수정 선택, 사진 1개, JPG·PNG·WebP, 기본 최대 10MB(서버 이미지 제한 설정 적용) |
| boothId | 선택, 기존 부스 ID |

부스 선택 목록은 [부스 API](frontend-booths-api.md)를 사용합니다. 수정 시 사진을 생략하면 기존 사진을 유지합니다. **수정 시 boothId를 생략하면 연결을 해제합니다.** 연결 유지 시 기존 ID를 다시 보내세요.

```js
const body = new FormData();
body.append("name", name);
body.append("description", description);
if (image) body.append("image", image);
if (boothId != null) body.append("boothId", String(boothId));
const response = await fetch("/api/sponsors", {
  method: "POST", // 수정: PUT /api/sponsors/{id}
  credentials: "include",
  headers: { Authorization: `Bearer ${accessToken}` },
  body,
});
```

`Content-Type`은 직접 설정하지 마세요. 브라우저가 boundary를 설정합니다.

파일 파트·MIME·요청 전체 크기의 공통 제한은 [업로드 규약](api-upload-limits.md)을 참고합니다. 수정도 `name`, `description`을 모두 전송해야 합니다.

## 오류·운영

- 입력 오류: `SPONSOR_INVALID_INPUT`
- 이미지 오류: `SPONSOR_IMAGE_REQUIRED`, `UNSUPPORTED_SPONSOR_IMAGE_TYPE`, `SPONSOR_IMAGE_TOO_LARGE`
- 대상 없음: `SPONSOR_NOT_FOUND`, `BOOTH_NOT_FOUND`
- 관리 권한 없음: `SPONSOR_MANAGE_FORBIDDEN`. 인증 필터의 공통 인증·권한 오류가 먼저 반환될 수도 있습니다.
- 저장소 오류: `SPONSOR_IMAGE_UPLOAD_FAILED`
- 부스를 삭제해도 협찬사는 유지되고 부스 연결만 해제됩니다.
- DB 반영 후 기존 사진을 정리하며, 롤백 시 새 업로드 사진을 정리합니다.
- 기존 R2 설정을 사용합니다. 저장 경로: `festa2026_sponsors/`.
- 신규 DB 테이블: `festival_sponsors`. 배포 환경의 스키마 관리 정책에 따라 생성이 필요합니다.

입력·이미지 검증 오류는 400, 대상 없음은 404, 관리 권한 없음은 403, 이미지 업로드 실패는 503입니다. multipart 사전 검사에서 `MULTIPART_MANAGE_FORBIDDEN`이 먼저 반환될 수도 있습니다.

협찬사의 생성자·수정자 UUID는 내부 DB에 저장하며 공개 응답에는 포함하지 않습니다. 현재 축제 이용 정보 삭제 시 이 운영 메타데이터는 익명화하지 않습니다.
