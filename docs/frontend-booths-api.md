# 부스 지도 API

모든 시각은 축제 당일의 `HH:mm:ss` 형식이며, 좌표는 WGS84 위도·경도입니다. 전체 조회와 상세 조회는 비로그인 사용자도 호출할 수 있습니다. 선택적으로 Bearer 토큰을 보내면 `favorited`에 현재 사용자의 찜 여부가 반영됩니다. 전체 찜 수는 어떤 응답에도 공개하지 않습니다.

## 공개 조회

- `GET /api/booths`: 지도에 표시할 전체 부스 핀 목록
- `GET /api/booths/{boothId}`: 선택한 부스 상세와 통합 정렬 미디어

목록 응답에는 `id`, `latitude`, `longitude`, `name`, `operator`, `opensAt`, `closesAt`, `stampEnabled`, `representativeMedia`, `favorited`가 포함됩니다. `stampEnabled`는 스탬프 지급 부스 여부입니다. 상세 응답의 `media`는 이미지와 동영상이 섞인 노출 순서이며 각 항목은 `kind`, `url`, `displayOrder`, `representative`를 가집니다.

## 찜

Bearer Access Token이 필수입니다.

- `POST /api/booths/{boothId}/favorite`: 찜 등록. 이미 등록되어도 성공하는 멱등 요청이며 `204`를 반환합니다.
- `DELETE /api/booths/{boothId}/favorite`: 찜 해제. 이미 해제되어도 `204`를 반환합니다.
- `GET /api/users/me/favorite-booths`: 내가 찜한 부스 목록

## ADMIN 이상 관리 API

- `POST /api/booths`: 기본 정보, `stampEnabled`, 복수 `managerUuids` 등록
- `PATCH /api/booths/{boothId}`: 기본 정보와 담당자 수정
- `POST /api/booths/{boothId}/images`: `files` multipart 이미지 업로드, 최대 5개
- `POST /api/booths/{boothId}/videos`: `files` multipart 동영상 업로드, 최대 3개
- `PATCH /api/booths/{boothId}/media/order`: 통합 순서와 대표 항목 변경
- `DELETE /api/booths/{boothId}/media/{mediaId}`: 미디어 한 개 삭제
- `DELETE /api/booths/{boothId}`: 부스 삭제

순서 변경 본문 예시:

```json
{
  "mediaIds": [12, 9, 15],
  "representativeMediaId": 9
}
```

`mediaIds`에는 해당 부스의 현재 미디어 ID를 빠짐없이 한 번씩 보내야 합니다. 대표 항목은 이미지 또는 동영상 모두 가능합니다. 처음 업로드할 때는 통합 순서의 첫 항목이 자동으로 대표가 됩니다.

관리자 웹 화면은 `/admin/booths`이며 `ADMIN`, `SUPER_ADMIN`만 접근할 수 있습니다. 담당자 후보는 축제 서비스에 한 번 이상 로그인해 로컬 사용자와 연결된 계정을 SSO 프로필로 표시합니다.
