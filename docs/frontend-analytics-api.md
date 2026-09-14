# React 프런트 이벤트 로깅 연동 가이드

이 API는 API 호출 기록과 별도로 React SPA에서 실제로 열린 페이지와 주요 사용자 행동을 수집합니다.
사용자 이름, 이메일, 입력값, 전체 URL, 쿼리 문자열 및 토큰은 이벤트에 넣지 않습니다.

인증 헤더, 토큰 rotation과 공통 오류 형식은 [공통 API 규약](frontend-api-common.md)을 따릅니다. 분석 전송은 핵심 사용자 기능보다 항상 낮은 우선순위로 처리합니다.

## API 계약

```http
POST /api/analytics/events
Content-Type: application/json
Authorization: Bearer {accessToken}  # 선택: 로그인 상태일 때만 전송
Cookie: festivalRefreshToken=...     # 로그인 상태에서 브라우저가 자동 전송
```

한 요청에는 이벤트를 1~20개까지 넣을 수 있습니다.

```json
{
  "sessionId": "da4d31fd-6ccb-4cba-a44f-20f27486f73a",
  "appVersion": "2026.10.0",
  "events": [
    {
      "eventId": "65d89a76-e521-40ee-80ed-4e27d17c58c2",
      "type": "PAGE_VIEW",
      "route": "/performances",
      "targetId": null,
      "occurredAt": "2026-10-06T01:30:00.000Z",
      "durationMs": null
    },
    {
      "eventId": "e86a439d-c6f3-4662-b769-adf96b6e45c5",
      "type": "PERFORMANCE_DETAIL_VIEW",
      "route": "/performances/:id",
      "targetId": "15",
      "occurredAt": "2026-10-06T01:30:04.000Z",
      "durationMs": null
    }
  ]
}
```

정상적으로 큐에 들어가면 `202 Accepted`를 반환합니다.

```json
{
  "acceptedEvents": 2
}
```

`acceptedEvents`가 전송 개수보다 작으면 일부 이벤트가 큐에 들어가지 못한 것입니다. 큐 포화뿐 아니라 서버에서 수집 기능을 비활성화한 경우에도 0을 반환합니다. 동일한 `eventId`로 제한적으로 재시도할 수 있지만, 0이 반복되면 전송을 중단하거나 간격을 늘립니다. 서버는 `eventId`를 기준으로 중복 저장을 방지합니다. `202`는 큐 접수 결과이며 DB 저장 완료를 보장하지 않습니다.

비로그인 사용자는 Authorization 헤더 없이 호출하며 서버에는 `userUuid = null`로 저장됩니다. 로그인 사용자가 Access Token을 보내면 SSO에서 검증한 `userUuid`가 연결됩니다. 잘못되거나 만료 후 갱신할 수 없는 토큰을 보낸 요청은 익명으로 처리하지 않고 `401`을 반환합니다.

Access Token이 자동 갱신되면 응답의 `X-Access-Token`에 새 토큰이 들어갑니다. 이 값을 React 메모리의 토큰으로 교체해야 합니다. Refresh Token은 JavaScript로 읽지 않고 `credentials: "include"`로만 전달합니다.

## 허용 이벤트

| 이벤트 | 용도 | targetId 예시 |
|---|---|---|
| `PAGE_VIEW` | 일반 페이지 진입 | `null` |
| `PAGE_LEAVE` | 페이지 이탈 및 체류 시간 | `null` |
| `PERFORMANCE_DETAIL_VIEW` | 공연 상세 조회 | 공연 ID |
| `BOOTH_DETAIL_VIEW` | 부스 상세 조회 | 부스 ID |
| `NOTICE_DETAIL_VIEW` | 공지 상세 조회 | 공지 ID |
| `MAP_VIEW` | 축제 지도 조회 | `null` |
| `QR_PAGE_VIEW` | 내 QR 화면 조회 | `null` |
| `EXTERNAL_LINK_CLICK` | 관리 대상 외부 링크 클릭 | `instagram`, `youtube` 등 사전에 정한 ID |

서버 코드를 수정하지 않고 임의의 이벤트 이름을 보낼 수는 없습니다.

## 필드 규칙

- `sessionId`: 브라우저 탭 세션을 구분하는 UUID입니다. 개인정보가 아니며 `sessionStorage`에 보관할 수 있습니다.
- `eventId`: 이벤트마다 새로 생성하는 UUID입니다. 네트워크 재시도 시에는 기존 값을 그대로 사용합니다.
- `route`: 최대 200자, `/`로 시작하고 영문·숫자·`_`·`:`·`/`·`.`·`-`만 사용하는 정규화된 라우트입니다. 한글 경로나 URL 인코딩 문자열을 그대로 보내지 않습니다.
- `targetId`: 영문, 숫자, `_`, `-`만 가능하며 최대 100자입니다.
- `occurredAt`: UTC ISO-8601 시각입니다. 전송 시점 기준 과거 24시간부터 미래 5분까지만 허용됩니다.
- `durationMs`: `PAGE_LEAVE`에서만 0 이상 12시간 이하로 사용할 수 있습니다. 다른 이벤트는 생략하거나 null을 보냅니다.
- `appVersion`: 선택값이며 최대 50자입니다.

`targetId`는 `PERFORMANCE_DETAIL_VIEW`, `BOOTH_DETAIL_VIEW`, `NOTICE_DETAIL_VIEW`, `EXTERNAL_LINK_CLICK`에서 필수입니다. 나머지 이벤트에서는 생략/null이어야 하며 빈 문자열도 허용되지 않습니다. 한 배치 안의 중복 `eventId`는 400입니다. 이벤트 하나라도 검증에 실패하면 배치 전체가 거절됩니다.

라우트 파라미터를 실제 값으로 보내지 마세요.

```text
권장:   /performances/:id + targetId: "15"
비권장: /performances/15
금지:   /search?keyword=학생이름
```

## React/TypeScript 기본 구현

Access Token은 프로젝트의 기존 메모리 토큰 저장소에서 가져오도록 연결합니다.

```ts
type FrontendEventType =
  | "PAGE_VIEW"
  | "PAGE_LEAVE"
  | "PERFORMANCE_DETAIL_VIEW"
  | "BOOTH_DETAIL_VIEW"
  | "NOTICE_DETAIL_VIEW"
  | "MAP_VIEW"
  | "QR_PAGE_VIEW"
  | "EXTERNAL_LINK_CLICK";

type AnalyticsEvent = {
  eventId: string;
  type: FrontendEventType;
  route: string;
  targetId: string | null;
  occurredAt: string;
  durationMs: number | null;
};

const SESSION_KEY = "festivalAnalyticsSessionId";
const MAX_BUFFER_SIZE = 100;
const FLUSH_SIZE = 10;
let buffer: AnalyticsEvent[] = [];
let flushing = false;

function getSessionId(): string {
  const saved = sessionStorage.getItem(SESSION_KEY);
  if (saved) return saved;
  const created = crypto.randomUUID();
  sessionStorage.setItem(SESSION_KEY, created);
  return created;
}

export function trackEvent(
  event: Omit<AnalyticsEvent, "eventId" | "occurredAt" | "durationMs"> &
    Partial<Pick<AnalyticsEvent, "durationMs">>,
) {
  buffer.push({
    eventId: crypto.randomUUID(),
    occurredAt: new Date().toISOString(),
    durationMs: event.durationMs ?? null,
    ...event,
  });

  if (buffer.length > MAX_BUFFER_SIZE) {
    buffer = buffer.slice(-MAX_BUFFER_SIZE);
  }
  if (buffer.length >= FLUSH_SIZE) void flushAnalytics();
}

// getAccessToken/setAccessToken은 기존 인증 상태 관리 함수로 교체합니다.
export async function flushAnalytics(keepalive = false) {
  const accessToken = getAccessToken();
  if (buffer.length === 0 || flushing) return;

  flushing = true;
  const events = buffer.splice(0, 20);
  try {
    const response = await fetch("/api/analytics/events", {
      method: "POST",
      credentials: "include",
      keepalive,
      headers: {
        "Content-Type": "application/json",
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      },
      body: JSON.stringify({
        sessionId: getSessionId(),
        appVersion: import.meta.env.VITE_APP_VERSION ?? null,
        events,
      }),
    });

    const refreshedAccessToken = response.headers.get("X-Access-Token");
    if (refreshedAccessToken) setAccessToken(refreshedAccessToken);

    if (!response.ok) {
      // 잘못된 입력이나 인증·권한 오류는 같은 배치로 재시도하지 않습니다.
      if ([400, 401, 403].includes(response.status)) return;
      if (![429, 502, 503].includes(response.status)) return;
      throw new Error(`analytics request failed: ${response.status}`);
    }
    const result: { acceptedEvents: number } = await response.json();
    if (result.acceptedEvents < events.length) {
      buffer.unshift(...events);
    }
  } catch {
    // 분석 실패가 사용자 화면을 방해하면 안 됩니다.
    buffer.unshift(...events);
  } finally {
    if (buffer.length > MAX_BUFFER_SIZE) buffer.length = MAX_BUFFER_SIZE;
    flushing = false;
  }
}

setInterval(() => void flushAnalytics(), 5_000);

document.addEventListener("visibilitychange", () => {
  if (document.visibilityState === "hidden") void flushAnalytics(true);
});
```

`navigator.sendBeacon()`은 Authorization 헤더를 설정할 수 없으므로 이 API에는 사용하지 않습니다. 페이지 종료 시에는 작은 요청 본문과 `fetch(..., { keepalive: true })`를 사용합니다.

위 코드는 기본 버퍼 예시입니다. 운영 연동에서는 `429`·`502`·`503`에 대한 지수 백오프와 최대 재시도 횟수, `acceptedEvents=0` 반복 시 중단 처리를 추가합니다. 인증 만료 시의 전역 상태 정리는 공통 인증 처리에 연결하며 분석 오류 토스트는 표시하지 않습니다.

## React Router 페이지 조회

페이지별 라우트 이름은 프런트에서 명시적으로 정규화하는 것이 가장 안전합니다.

```tsx
import { useEffect } from "react";
import { useLocation, matchPath } from "react-router-dom";

function normalizeRoute(pathname: string): { route: string; targetId: string | null } {
  const performance = matchPath("/performances/:id", pathname);
  if (performance) {
    return { route: "/performances/:id", targetId: performance.params.id ?? null };
  }
  return { route: pathname, targetId: null };
}

export function AnalyticsRouteTracker() {
  const location = useLocation();

  useEffect(() => {
    const current = normalizeRoute(location.pathname);
    trackEvent({
      type: current.route === "/performances/:id"
        ? "PERFORMANCE_DETAIL_VIEW"
        : "PAGE_VIEW",
      route: current.route,
      targetId: current.targetId,
    });
  }, [location.pathname]);

  return null;
}
```

라우터 내부에서 한 번만 렌더링합니다.

```tsx
<BrowserRouter>
  <AnalyticsRouteTracker />
  <AppRoutes />
</BrowserRouter>
```

React 개발 모드의 `StrictMode`에서는 effect가 두 번 실행될 수 있습니다. 개발 환경 통계가 필요 없다면 개발 모드에서 전송을 끄거나, 같은 페이지를 짧은 시간 안에 중복 기록하지 않도록 프런트에서 방지하세요.

## 실패 처리

- `400 INVALID_FRONTEND_EVENT`: 이벤트 필드, 라우트 또는 시각 오류입니다. 같은 잘못된 이벤트를 재시도하지 않습니다.
- `401`: Bearer Token을 보냈지만 Access/Refresh Token이 모두 유효하지 않습니다. 로그인 상태를 해제하고 필요 시 로그인 화면으로 이동합니다.
- `403`: 사용할 수 없는 계정입니다.
- `429 FRONTEND_ANALYTICS_RATE_LIMIT`: 잠시 기다린 뒤 지수 백오프로 재시도합니다.
- `502`, `503`: SSO 또는 서버가 일시적으로 불안정합니다. 사용자 화면에는 오류를 표시하지 않고 제한적으로 재시도합니다.

분석 전송 실패는 공연 조회, QR, 지도 등 실제 사용자 기능을 중단시키면 안 됩니다.

## 수집 금지 정보

- Access Token, Refresh Token
- 이름, 이메일, 전화번호, 학번
- 검색어와 폼 입력값
- 전체 referrer URL
- 쿼리 문자열과 URL fragment
- 정확한 위치 정보
- 임의의 JSON metadata

서버는 `userUuid`를 요청에서 받지 않습니다. 비로그인 이벤트는 `null`, 로그인 이벤트는 SSO 인증 결과의 `userUuid`로 설정합니다.
