# 동적 사용자 QR API

이 문서는 사용자가 자신의 QR을 표시하는 기능과 운영자가 일반 사용자 정보를 조회하는 API를 설명합니다. 부스 스탬프 지급은 [스탬프 API](frontend-stamps-api.md)를 사용합니다.

## 내 QR 토큰 발급

```http
POST /api/qr/tokens
Authorization: Bearer ACCESS_TOKEN
```

성공 `200`:

```json
{
  "token": "temporary-random-token",
  "expiresAt": "2026-08-18T03:01:00Z"
}
```

- QR 이미지에는 응답의 `token` 문자열만 넣습니다.
- Access Token, `userUuid`, 이름이나 학번을 QR에 넣지 않습니다.
- 기본 유효시간은 60초입니다. `expiresAt` 전에 새 토큰을 발급하고 QR 이미지를 교체합니다.
- 새 토큰 발급이 기존 토큰을 즉시 폐기하지는 않습니다. 각 토큰은 자신의 만료 시각까지 유효합니다.
- 토큰을 URL query, analytics, 콘솔이나 영구 저장소에 기록하지 않습니다.

```tsx
const [qr, setQr] = useState<{ token: string; expiresAt: string } | null>(null);

useEffect(() => {
  let timer: number | undefined;
  const refresh = async () => {
    const next = await apiFetch<{ token: string; expiresAt: string }>("/api/qr/tokens", {
      method: "POST",
      auth: true,
    });
    setQr(next);
    const delay = Math.max(5_000, new Date(next.expiresAt).getTime() - Date.now() - 5_000);
    timer = window.setTimeout(refresh, delay);
  };
  void refresh();
  return () => window.clearTimeout(timer);
}, []);
```

## 일반 QR 사용자 조회

```http
POST /api/qr/scan
Authorization: Bearer OPERATOR_ACCESS_TOKEN
Content-Type: application/json

{ "token": "scanned-token" }
```

`USER`는 사용할 수 없습니다. 응답은 조회자의 최고 축제 권한에 따라 필드가 제한되며 `null` 필드는 JSON에서 생략될 수 있습니다.

| 조회 권한 | 제공 범위 |
|---|---|
| `BOOTH_MANAGER` | 마스킹 이름·학번, 학과, 학년 |
| `STAFF` | 마스킹 이름·학번, 학과, 학년 |
| `ADMIN` | 원본 이름·학번, 학과, 학년 + 전화번호, 이메일 |
| `SUPER_ADMIN` | SSO 전체 프로필 + 축제 권한 |

이 API는 일반 정보 조회용입니다. 스탬프 지급 화면에서는 `/api/booths/{boothId}/stamps/qr/lookup`을 사용해야 담당 부스와 `stampEnabled`가 검증됩니다.

## 사용자 정보 검색

```http
POST /api/qr/search
Authorization: Bearer OPERATOR_ACCESS_TOKEN
Content-Type: application/json

{ "query": "컴퓨터공학과", "page": 0, "size": 20 }
```

- `STAFF`, `ADMIN`, `SUPER_ADMIN`만 사용할 수 있습니다. `BOOTH_MANAGER`는 기존 QR 조회만 가능합니다.
- 검색어는 2~100자이며 이름, 학번, 로그인 ID, 이메일, 전화번호, 학과, 재학 상태, 사용자 UUID의 원본 정보와 비교합니다.
- 원본 정보로 검색하더라도 결과 필드는 위 권한 표에 따라 마스킹·제한됩니다. 예를 들어 STAFF가 `홍길동`으로 검색해도 결과 이름은 `홍*동`입니다.
- 축제 서비스에 한 번 이상 연동된 사용자만 검색 대상입니다.
- `page`는 0부터 시작하고 기본 페이지 크기는 20명입니다. `size`는 1~100 범위이며, 요청 페이지가 마지막 페이지보다 크면 마지막 페이지를 반환합니다.
- 이름처럼 결과가 적은 검색뿐 아니라 학과처럼 결과가 많은 검색도 `totalPages`를 기준으로 페이지 버튼을 구성하십시오. 페이지 이동 시 같은 `query`와 원하는 `page`를 다시 전송합니다.

```ts
type UserSearchResponse = {
  items: QrUserView[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};
```

`ADMIN` 이상 검색 결과에는 권한 변경에 필요한 `userUuid`, `festivalRoles`, 계산된 `managementRole`이 포함됩니다.

## 사용자 관리 권한 변경

```http
PATCH /api/qr/users/{userUuid}/role
Authorization: Bearer ADMIN_ACCESS_TOKEN
Content-Type: application/json

{ "managementRole": "STAFF" }
```

성공 응답:

```json
{
  "userUuid": "123e4567-e89b-12d3-a456-426614174099",
  "festivalRoles": ["STAFF"]
}
```

- `managementRole`은 `USER`, `STAFF`, `ADMIN`, `SUPER_ADMIN` 중 하나입니다.
- `ADMIN`은 자신이 아닌 `USER`·`STAFF` 사용자만 `USER ↔ STAFF` 범위에서 변경할 수 있습니다.
- `SUPER_ADMIN`은 모든 관리 권한을 변경할 수 있지만 마지막 SUPER_ADMIN은 강등할 수 없습니다.
- `BOOTH_MANAGER`는 별도 boolean과 담당 부스 관계로 관리하므로 이 API에서 지정하지 않습니다. 부스 지도 관리 화면에서 담당자로 지정하거나 해제합니다.
- 관리 권한을 변경해도 대상 사용자의 기존 부스 관리자 여부와 담당 부스 관계는 유지됩니다.

## 오류

- `400 QR_INVALID_OR_EXPIRED`: QR이 잘못됐거나 만료됨. 사용자에게 QR 새로고침 요청
- `401 UNAUTHORIZED`: 운영자 로그인 만료
- `403 QR_SCAN_FORBIDDEN`: 조회 권한 없음
- `400 INVALID_USER_SEARCH_QUERY`: 검색어 길이 오류
- `403 USER_SEARCH_FORBIDDEN`: 사용자 검색 권한 없음
- `400 INVALID_MANAGEMENT_ROLE`: BOOTH_MANAGER 등 잘못된 관리 권한 요청
- `403 USER_ROLE_MANAGE_FORBIDDEN`: ADMIN 미만의 권한 변경 요청
- `403 USER_ROLE_ESCALATION_FORBIDDEN`: ADMIN의 자기 권한·상위 권한 변경 시도
- `404 FESTIVAL_USER_NOT_FOUND`: 축제 연동 사용자가 아님
- `409 LAST_SUPER_ADMIN_REQUIRED`: 마지막 SUPER_ADMIN 강등 시도
