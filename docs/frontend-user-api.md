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
  schoolVerificationStatus: "UNVERIFIED" | "VERIFIED" | "REVOKED";
  schoolVerified: boolean;
  schoolVerifiedAt: string | null;
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
# 학교 학생 인증

`GET /api/users/me` 응답에는 축제 서비스가 관리하는 다음 필드가 포함됩니다.

```json
{
  "schoolVerificationStatus": "VERIFIED",
  "schoolVerified": true,
  "schoolVerifiedAt": "2026-08-29T10:30:00Z"
}
```

직접 입력으로 가입한 회원의 초기 상태는 `UNVERIFIED`입니다. 학교 SSO로 가입했거나 가입 후 학교 인증을 완료한 회원은 `VERIFIED`입니다.

가입 후 인증을 시작하려면 로그인 상태에서 다음 API를 호출합니다.

```http
POST /api/users/me/school-verification/authorize
Authorization: Bearer ACCESS_TOKEN
```

```json
{
  "authorizeUrl": "https://www.syu.ac.kr/festa-sso/authorize?..."
}
```

응답의 `authorizeUrl`로 브라우저를 이동합니다. 학교 인증 성공 후 `school-sso.return-url`로 `?schoolVerification=success`가 붙어 돌아옵니다. `access_denied`, `failed`, `invalid_state`도 같은 파라미터 값으로 반환될 수 있습니다.

동일한 학교 학생은 하나의 축제 계정에만 연결할 수 있습니다. 가입 후 중복 연결이면 콜백 결과가 `?schoolVerification=already_linked`로 반환됩니다. 축제 DB에는 학교 식별자 원문 대신 HMAC-SHA256 해시만 저장합니다.
