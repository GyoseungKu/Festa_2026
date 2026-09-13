# 공통 API 규약

모든 기능 문서에 공통으로 적용되는 프런트 구현 규칙입니다.

## Base URL과 CORS

- 운영: `https://festa.syu-likelion.org`
- 로컬 프런트: `http://localhost:5173`
- 로컬 API 기본값: `http://localhost:8888`

프런트와 API가 같은 Origin이면 상대 경로(`/api/...`)를 사용합니다. 다른 Origin에서 개발할 때만 `VITE_API_BASE_URL` 등을 사용합니다.

```ts
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";
```

## 인증과 쿠키

- Access Token: React 메모리에만 보관하고 보호 API에 Bearer 헤더로 전송합니다.
- Refresh Token: HttpOnly 쿠키이므로 JavaScript에서 읽지 않습니다.
- 모든 요청: `credentials: "include"`를 사용합니다.
- 공개 API: 로그인 상태를 반영할 필요가 있을 때만 Bearer 헤더를 보냅니다. 만료된 토큰을 계속 보내면 공개 API도 `401`이 될 수 있습니다.

서버가 Access Token을 자동 갱신하면 응답 헤더 `X-Access-Token`에 새 토큰을 반환합니다. 성공·실패와 관계없이 헤더를 먼저 확인하십시오.

## 공통 TypeScript 타입

```ts
export type ApiError = {
  code: string;
  message: string;
  timestamp: string;
};

export class ApiRequestError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
  ) {
    super(message);
  }
}
```

## 공통 fetch 래퍼 예시

프로젝트의 인증 store 함수 이름에 맞게 `getAccessToken`, `setAccessToken`을 교체합니다.

```ts
type ApiFetchOptions = RequestInit & {
  auth?: boolean;
};

export async function apiFetch<T>(
  path: string,
  { auth = false, headers, ...init }: ApiFetchOptions = {},
): Promise<T> {
  const accessToken = auth ? getAccessToken() : null;
  const requestHeaders = new Headers(headers);
  if (accessToken) requestHeaders.set("Authorization", `Bearer ${accessToken}`);
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    credentials: "include",
    headers: requestHeaders,
  });

  const rotated = response.headers.get("X-Access-Token");
  if (rotated) setAccessToken(rotated);

  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as ApiError | null;
    throw new ApiRequestError(
      response.status,
      body?.code ?? "UNKNOWN_ERROR",
      body?.message ?? "요청을 처리하지 못했습니다.",
    );
  }

  if (response.status === 204) return undefined as T;
  const text = await response.text();
  return text.trim() ? (JSON.parse(text) as T) : (undefined as T);
}
```

JSON 요청은 호출부에서 명시합니다.

```ts
await apiFetch<ResponseType>("/api/example", {
  method: "POST",
  auth: true,
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(requestBody),
});
```

`FormData` 요청에는 브라우저가 boundary를 붙이도록 `Content-Type` 헤더를 생략합니다.

학생 인증 승인·삭제처럼 성공 코드가 `200`이어도 본문이 없는 API가 있습니다. 위 래퍼는 빈 본문을 `undefined`로 처리합니다. 학교 SSO 시작·콜백의 `302` 리다이렉트는 이 JSON 래퍼가 아니라 브라우저 페이지 이동으로 처리합니다.

## 공통 오류 응답

```json
{
  "code": "INVALID_REQUEST",
  "message": "요청 정보를 확인해 주세요.",
  "timestamp": "2026-08-18T00:00:00Z"
}
```

| HTTP | 대표 code | 프런트 처리 |
|---|---|---|
| `400` | `INVALID_REQUEST`, `INVALID_PARAMETER` | 필드 오류 표시, 동일 요청 자동 재시도 금지 |
| `401` | `UNAUTHORIZED` | 메모리 토큰과 사용자 캐시 제거 후 로그인 유도 |
| `403` | 도메인별 `*_FORBIDDEN` | 권한 부족 안내 또는 접근 화면 제거 |
| `404` | 도메인별 `*_NOT_FOUND` | 목록으로 이동하거나 삭제된 항목 안내 |
| `409` | 도메인별 충돌 code | 현재 상태를 다시 조회해 UI 동기화 |
| `413` | `UPLOAD_TOO_LARGE` | 파일 크기 안내 |
| `415` | `UNSUPPORTED_MEDIA_TYPE` | 파일 또는 요청 Content-Type 확인 |
| `429` | rate-limit code | `Retry-After`가 있으면 따르고 지수 백오프 |
| `502`, `503` | SSO/R2 장애 code | 잠시 후 재시도 안내 |
| `500` | `INTERNAL_ERROR` | 일반 오류 안내, 요청 데이터는 콘솔에 출력하지 않음 |

## 날짜, nullable, 페이지네이션

- `Instant`: `2026-08-18T03:00:00Z` 형식입니다.
- `LocalTime`: `10:00:00` 형식입니다.
- SSO 프로필과 대표 미디어는 `null`일 수 있으므로 타입에 반영합니다.
- 페이지 번호는 0부터 시작합니다.
- 페이지 응답은 보통 `items`, `page`, `size`, `totalElements`, `totalPages`를 사용합니다.

```ts
export type PageResponse<T> = {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};
```

## 재시도 기준

- 자동 재시도 가능: 네트워크 오류, 제한적인 `429`, `502`, `503`.
- 자동 재시도 금지: `400`, `401`, `403`, 대부분의 `404`, 파일 업로드.
- 생성 요청을 재시도할 때는 도메인 중복 정책을 확인합니다.
- 분석 이벤트 외에는 별도의 idempotency key가 없습니다.
