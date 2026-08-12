# React 접속 현황 heartbeat 연동 가이드

관리자 모니터링의 “현재 접속 추정”은 로그인 사용자 목록이 아니라 최근 150초 안에 heartbeat를 보낸 브라우저 세션 수입니다. 개인정보와 Access Token은 수집하지 않습니다.

## API

```http
POST /api/presence/heartbeat
Content-Type: application/json
```

```json
{
  "sessionId": "4e381dbe-8804-4d3c-b5c0-c2080ac095f2",
  "route": "/performances"
}
```

- 인증은 필요하지 않습니다.
- 성공 응답은 `204 No Content`입니다.
- `sessionId`는 브라우저 탭의 `sessionStorage`에 만든 UUID를 사용합니다.
- `route`에는 React Router의 pathname만 보내고 query string과 hash는 제거합니다.
- heartbeat는 첫 화면 진입, route 변경, 탭이 다시 보이는 시점과 이후 60초마다 전송합니다.
- 탭을 닫아 별도 종료 이벤트를 못 보내더라도 서버 TTL로 자동 제외됩니다.

## React 예시

```tsx
import { useEffect } from "react";
import { useLocation } from "react-router-dom";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";
const SESSION_KEY = "festaPresenceSessionId";

function presenceSessionId(): string {
  const existing = sessionStorage.getItem(SESSION_KEY);
  if (existing) return existing;
  const created = crypto.randomUUID();
  sessionStorage.setItem(SESSION_KEY, created);
  return created;
}

export function PresenceHeartbeat() {
  const location = useLocation();

  useEffect(() => {
    const send = () => {
      if (document.visibilityState !== "visible") return;
      void fetch(`${API_BASE_URL}/api/presence/heartbeat`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({
          sessionId: presenceSessionId(),
          route: location.pathname,
        }),
        keepalive: true,
      });
    };

    send();
    const interval = window.setInterval(send, 60_000);
    document.addEventListener("visibilitychange", send);
    return () => {
      window.clearInterval(interval);
      document.removeEventListener("visibilitychange", send);
    };
  }, [location.pathname]);

  return null;
}
```

앱의 Router 내부 최상단에 `<PresenceHeartbeat />`를 한 번 배치합니다. 이 값은 “사람 수”가 아니라 활성 브라우저 탭 수에 가까운 추정치이며, 여러 서버 인스턴스로 확장하면 인스턴스별 메모리가 분리되므로 별도 집계 계층이 필요합니다.
