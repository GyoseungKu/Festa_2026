# 로그인 사용자 비밀번호 변경 연동 가이드

[문서 목차](README.md) · [프런트 연동 시작하기](frontend-getting-started.md)

이 API는 현재 비밀번호를 알고 있는 로그인 사용자가 마이페이지에서 사용하는 기능입니다. 비밀번호를 잊은 사용자는 로그인 전 계정 복구 API를 사용합니다.

공통 인증과 토큰 갱신은 [공통 API 규약](frontend-api-common.md)을 따릅니다.

## 요청

```http
PATCH /api/users/me/password
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "currentPassword": "CurrentPassword123!",
  "newPassword": "NewPassword456!"
}
```

- `currentPassword`: 필수, 최대 128자
- `newPassword`: 필수, 8–128자
- Refresh Token 쿠키 전달을 위해 `credentials: "include"`를 사용합니다.
- 비밀번호를 URL, analytics, 로그 또는 브라우저 저장소에 기록하지 않습니다.

## 성공 처리

성공 응답은 `204 No Content`이며 Festa Refresh Token 쿠키가 만료됩니다. 이 저장소에서 확인되는 동작은 SSO 변경 요청과 현재 브라우저의 쿠키 제거입니다. 다른 기기의 Access/Refresh Token 폐기 범위는 SSO 구현에 달려 있으므로 Festa 코드만으로 전체 세션 종료를 보장하지 않습니다.

서버는 React 메모리에 저장된 Access Token을 직접 제거할 수 없으므로 프런트가 반드시 다음 순서로 처리합니다.

1. 메모리의 Access Token 제거
2. 사용자 관련 React Query/상태 캐시 제거
3. 뒤로가기로 마이페이지에 다시 진입하지 못하도록 로그인 화면으로 replace 이동
4. “비밀번호가 변경되었습니다. 다시 로그인해 주세요.” 안내

```tsx
async function changePassword(currentPassword: string, newPassword: string) {
  await apiFetch<void>("/api/users/me/password", {
    method: "PATCH",
    auth: true,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ currentPassword, newPassword }),
  });

  authStore.clearAccessToken();
  queryClient.clear();
  navigate("/login", {
    replace: true,
    state: { message: "비밀번호가 변경되었습니다. 다시 로그인해 주세요." },
  });
}
```

## 오류 처리

| HTTP | 의미 | 화면 문구 |
|---|---|---|
| `400` | 현재 비밀번호 오류 또는 입력값 오류 | 현재 비밀번호와 새 비밀번호를 확인해 주세요. |
| `401` | Access Token 만료 및 자동 갱신 실패 | 로그인이 만료되었습니다. 다시 로그인해 주세요. |
| `403` | 정지·탈퇴 계정 | 사용할 수 없는 계정입니다. |
| `429` | 요청 제한 | 잠시 후 다시 시도해 주세요. |
| `503` | SSO 장애 | 인증 서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요. |

오류 시에는 사용자가 입력한 비밀번호 값을 응답 메시지, 오류 추적 도구 또는 콘솔에 포함하지 않습니다.
