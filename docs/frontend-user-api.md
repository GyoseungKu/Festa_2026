# 내 정보·계정 API

모든 API는 Bearer 인증이 필요합니다. 공통 토큰 갱신 처리는 [공통 API 규약](frontend-api-common.md)을 따릅니다.

## 내 정보 조회

```http
GET /api/users/me
Authorization: Bearer ACCESS_TOKEN
```

```ts
type FestivalRole = "SUPER_ADMIN" | "ADMIN" | "STAFF" | "BOOTH_MANAGER" | "USER";

type MeResponse = {
  userUuid: string;
  loginId: string;
  email: string;
  ssoRole: string;
  status: string;
  name: string | null;
  phone: string | null;
  studentNo: string | null;
  department: string | null;
  grade: number | null;
  enrollment: string | null;
  birthDate: string | null;
  createdAt: string | null;
  updatedAt: string | null;
  festivalRoles: FestivalRole[];
};
```

기존 SSO 계정은 프로필·학적 필드가 `null`일 수 있습니다. 화면에서 빈 값 처리를 반드시 합니다.

## 이름·전화번호 수정

```http
PATCH /api/users/me/profile
Content-Type: application/json
Authorization: Bearer ACCESS_TOKEN
```

```json
{ "name": "홍길동", "phone": "01012345678" }
```

- `name`: 선택, 최대 100자
- `phone`: 선택, 빈 문자열 또는 숫자 10~11자리
- 성공 시 갱신된 `MeResponse`를 반환하므로 사용자 캐시를 이 값으로 교체합니다.

## 이메일 변경

이메일 변경은 세 단계입니다. 세 요청에서 같은 이메일을 사용하십시오.

### 1. 인증번호 발송

```http
POST /api/users/me/email/verification
Content-Type: application/json

{ "email": "new@example.com" }
```

### 2. 인증번호 확인

```http
POST /api/users/me/email/verification/confirm
Content-Type: application/json

{ "email": "new@example.com", "code": "123456" }
```

### 3. 변경 적용

```http
PATCH /api/users/me/email
Content-Type: application/json

{ "email": "new@example.com" }
```

각 성공 응답은 `{ "message": "..." }`입니다. 마지막 성공 후 `/api/users/me`를 다시 조회합니다.

## 비밀번호 변경

현재 비밀번호를 사용하는 변경 절차는 [비밀번호 변경 문서](frontend-password-change-api.md)를 참고합니다.

## 계정 탈퇴

```http
DELETE /api/users/me
Authorization: Bearer ACCESS_TOKEN
```

성공은 `204 No Content`입니다. 이 요청은 축제 서비스 연결만 삭제하는 것이 아니라 SSO 계정 자체를 탈퇴시킵니다. 확인 모달에서 이 점을 명확히 표시하고 성공 후 모든 메모리 토큰·캐시를 제거합니다.
