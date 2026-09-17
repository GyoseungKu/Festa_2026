# React 접속 현황 heartbeat 연동 가이드

[문서 목차](README.md) · [프런트 연동 시작하기](frontend-getting-started.md)

관리자 모니터링의 “현재 접속 추정”은 로그인 사용자 목록이 아니라 최근 150초 안에 heartbeat를 보낸 브라우저 세션 수입니다. heartbeat 본문에는 임의 sessionId와 route만 보내고 이름·학번·Access Token을 넣지 않습니다. 이는 일반 HTTP 접속 로그와 별개이며, 요청 로그에는 IP 등 운영 정보가 기록될 수 있습니다.

공통 API 규약은 [공통 API 규약](frontend-api-common.md)을 참고하되, 이 API에는 인증과 쿠키가 필요하지 않습니다.

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
- `route`는 필수, 최대 200자이며 `/`로 시작해야 합니다. `//`로 시작하거나 `?`, `#`, 제어문자를 포함하면 400입니다.
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
        credentials: "omit",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          sessionId: presenceSessionId(),
          route: location.pathname,
        }),
        keepalive: true,
      }).catch(() => {
        // 네트워크 실패는 화면에 노출하지 않고 다음 주기에 다시 전송합니다.
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

## 실패 처리

- heartbeat 실패는 사용자 기능을 막거나 화면에 오류 토스트를 띄우지 않습니다.
- `400 INVALID_REQUEST`: UUID 또는 route 형식 오류이므로 동일 payload를 재시도하지 않습니다.
- `429 PRESENCE_CAPACITY_EXCEEDED`: 서버 추적 한도에 도달한 상태이므로 현재 탭에서는 전송 간격을 늘립니다.
- 네트워크 오류는 다음 60초 주기에서 자연스럽게 다시 시도합니다.
