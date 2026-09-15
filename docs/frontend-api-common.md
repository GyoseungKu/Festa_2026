# 공통 API 규약

[문서 목차](README.md) · [프런트 연동 시작하기](frontend-getting-started.md)

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
  retryAfterSeconds?: number;
};

export class ApiRequestError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly retryAfter: string | null = null,
    public readonly requestId: string | null = null,
    public readonly retryAfterSeconds: number | null = null,
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
      response.headers.get("Retry-After"),
      response.headers.get("X-Request-ID"),
      body?.retryAfterSeconds ?? null,
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
| `400` | `INVALID_MULTIPART_REQUEST` | 필수 파트와 multipart boundary 확인 |
| `401` | `UNAUTHORIZED` | 메모리 토큰과 사용자 캐시 제거 후 로그인 유도 |
| `403` | 도메인별 `*_FORBIDDEN` | 권한 부족 안내 또는 접근 화면 제거 |
| `403` | `ACCOUNT_FORBIDDEN` | SSO 계정 사용 불가, 로그인 상태 정리 |
| `404` | 도메인별 `*_NOT_FOUND` | 목록으로 이동하거나 삭제된 항목 안내 |
| `409` | 도메인별 충돌 code | 현재 상태를 다시 조회해 UI 동기화 |
| `405` | `METHOD_NOT_ALLOWED` | 경로에 맞는 HTTP 메서드 확인 |
| `413` | `UPLOAD_TOO_LARGE` | 파일 크기 안내 |
| `415` | `UNSUPPORTED_MEDIA_TYPE` | 파일 또는 요청 Content-Type 확인 |
| `429` | rate-limit code | `Retry-After`가 있으면 따르고 지수 백오프 |
| `502`, `503` | SSO/R2 장애 code | 잠시 후 재시도 안내 |
| `500` | `INTERNAL_ERROR` | 일반 오류 안내, 요청 데이터는 콘솔에 출력하지 않음 |

## 날짜, nullable, 페이지네이션

- `Instant`: `2026-08-18T03:00:00Z` 형식입니다.
- `LocalTime`: `10:00:00` 형식입니다.
- `LocalTime`에는 날짜·시간대가 없습니다. 부스 운영 시각을 `Date`로 파싱하거나 UTC로 변환하지 않습니다.
- 내 정보의 SSO `createdAt`, `updatedAt`은 `LocalDateTime`으로 시간대가 없는 문자열입니다. 다른 도메인의 `Instant`와 달리 `Z`를 임의로 붙이지 않습니다. `birthDate`는 `YYYY-MM-DD`입니다.
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

- 조회 요청은 네트워크 오류나 `429`, `502`, `503`에 한해 횟수 제한과 백오프를 적용할 수 있습니다. 공지·분실물 상세 GET은 조회수를 증가시키므로 재호출도 집계됩니다.
- 자동 재시도 금지: `400`, `401`, `403`, 대부분의 `404`, 파일 업로드.
- 생성·변경 요청은 네트워크 오류만으로 처리 실패를 확정할 수 없습니다. 상태를 재조회하고 사용자가 결과를 확인하게 합니다. 특히 복수 참여 투표 POST를 자동 반복하면 응답이 추가됩니다.
- 분석 이벤트 외에는 별도의 idempotency key가 없습니다.

## 구현 시 놓치기 쉬운 계약

- `auth: true`는 메모리 토큰을 붙이는 예시 옵션이며 로그인 복구 기능이 아닙니다. 새로고침 후에는 `/api/auth/token/refresh`로 먼저 토큰을 복구합니다. 보호 API는 Refresh 쿠키만으로 호출할 수 없습니다.
- CORS는 `/api/**`에 적용되며 허용 Origin은 `app.cors.allowed-origins` 설정값입니다. 허용 요청 헤더는 `Authorization`, `Content-Type`, 노출 응답 헤더는 `X-Access-Token`, `X-Request-ID`, `Retry-After`입니다. `Set-Cookie`는 브라우저가 처리합니다.
- `Retry-After`는 모든 429에서 보장되지 않습니다. 있으면 초 단위 값 또는 HTTP 날짜로 해석하고, 없으면 자체 백오프를 사용합니다. 요청 식별자는 오류 문의에 사용하고 개인정보·토큰·본문은 함께 기록하지 않습니다.
- 보호된 multipart 요청은 본문 검증 전에 인증·역할 검사를 거칩니다. 도메인 오류보다 `401 UNAUTHORIZED` 또는 `403 MULTIPART_MANAGE_FORBIDDEN`이 먼저 나올 수 있습니다.
- SSO 401/403을 공통 예외 처리한 경우 Refresh 쿠키를 제거합니다. 역할 부족이나 읽기 전용 등 모든 403을 계정 탈퇴로 해석하지 않습니다.
- 토큰 갱신 헤더는 있는 경우 반영하되, 이후 비즈니스 처리에 실패한 모든 응답에 실린다고 가정하지 않습니다.
- `PATCH`가 모두 부분 수정은 아닙니다. 부스·공연·분실물·일반 공지와 투표 settings는 필수 필드를 다시 보내야 합니다. 생략/null의 의미는 도메인 문서를 따릅니다.
- DTO의 `@Size` 문자열 제한은 Java UTF-16 길이 기준으로, 일반적으로 JS `value.length`와 같습니다. DB 컬럼 길이만으로 API 제한을 판단하지 않습니다. 대나무숲 본문·닉네임과 생일 쪽지의 서비스 검증은 Unicode 코드포인트 기준이므로 해당 문서의 별도 규칙을 적용합니다.
- 권한으로 숨긴 QR·생일 관리자 프로필 필드는 null 대신 JSON에서 빠질 수 있습니다. `field == null` 등으로 누락과 null을 함께 처리합니다.
- 일반 페이지의 `items` 형태를 모든 API에 적용하지 않습니다. 부스·공연·투표 목록은 배열, 대나무숲은 커서, 투표 관리자 상세는 `submissions`, 스탬프 이력은 `history`를 사용합니다. 서버가 보정한 `page`와 `size`를 기준으로 UI를 갱신합니다.
- `/admin/**`는 관리자 쿠키와 CSRF를 사용하는 별도 웹 인터페이스입니다. [관리자 웹 경로](admin-web-api.md)를 참고합니다.
