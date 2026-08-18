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
| `STAFF` | 이름, 학번, 학과, 학년 |
| `ADMIN` | STAFF 범위 + 전화번호, 이메일 |
| `SUPER_ADMIN` | SSO 전체 프로필 + 축제 권한 |

이 API는 일반 정보 조회용입니다. 스탬프 지급 화면에서는 `/api/booths/{boothId}/stamps/qr/lookup`을 사용해야 담당 부스와 `stampEnabled`가 검증됩니다.

## 오류

- `400 QR_INVALID_OR_EXPIRED`: QR이 잘못됐거나 만료됨. 사용자에게 QR 새로고침 요청
- `401 UNAUTHORIZED`: 운영자 로그인 만료
- `403 QR_SCAN_FORBIDDEN`: 조회 권한 없음
