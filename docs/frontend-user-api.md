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

## 개인정보 수정

```http
PATCH /api/users/me/profile
Content-Type: application/json
Authorization: Bearer ACCESS_TOKEN
```

```json
{
  "phone": "01012345678",
  "department": "소프트웨어학과",
  "grade": 3,
  "enrollment": "ENROLLED"
}
```

- `phone`: 선택, 숫자 9~15자리; 다른 회원과 중복 불가
- `department`: 선택, 공백 불가, 최대 100자
- `grade`: 선택, 1~6
- `enrollment`: 선택, `ENROLLED` 또는 `LEAVE`
- 필드를 생략하면 기존 값이 유지되며, 최소 하나의 필드를 보내야 합니다.
- 이름, 학번, 생년월일은 본인이 변경할 수 없습니다.
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
## 학교 학생 인증

`GET /api/users/me` 응답에는 축제 서비스가 관리하는 다음 필드가 포함됩니다.

```json
{
  "schoolVerificationStatus": "VERIFIED",
  "schoolVerified": true,
  "schoolVerifiedAt": "2026-08-29T10:30:00Z"
}
```

직접 입력으로 가입한 회원의 초기 상태는 `UNVERIFIED`입니다. 학교 SSO로 가입했거나 가입 후 학교 인증을 완료한 회원은 `VERIFIED`입니다.

`schoolVerifiedAt`은 현재 구현상 학교 SSO 토큰을 축제 서버가 확인한 시각입니다. 관리자 승인 경로에서는 실제 승인 시각이 아니라 최초 승인 요청을 만든 콜백 시각이 유지됩니다.

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

응답의 `authorizeUrl`로 브라우저를 이동합니다. 콜백 결과는 `school-sso.return-url`의 `schoolVerification` 쿼리로 전달됩니다.

같은 브라우저 세션에서는 한 번에 하나의 학교 인증만 진행합니다. 여러 탭에서 다시 인증을 시작하면 기존 state가 덮어써져 먼저 시작한 인증이 `invalid_state`로 실패할 수 있으므로, 인증 화면을 연 뒤에는 인증 버튼의 중복 실행을 막습니다.

| 값 | 프런트 처리 |
|---|---|
| `success` | 인증 완료 후 내 정보 다시 조회 |
| `pending_approval` | 이름 또는 학번 불일치, 관리자 승인 대기 안내 |
| `department_update_required` | 학과 변경 확인 화면 표시 |
| `already_linked` | 다른 축제 계정에 연결된 학교 정보 안내 |
| `access_denied`, `failed`, `invalid_state` | 취소·실패 안내 |

학과 변경 확인 화면에서는 다음 API로 현재 회원정보와 학교 학적정보를 조회합니다.

```http
GET /api/users/me/school-verification/department
Authorization: Bearer ACCESS_TOKEN
```

```json
{
  "currentDepartment": "경영학과",
  "schoolDepartment": "컴퓨터공학과"
}
```

사용자가 변경에 동의하면 `POST /api/users/me/school-verification/department/confirm`을 호출합니다. 요청 본문은 없으며, 서버가 학교 학과로 SSO 회원정보를 수정한 뒤 학생 인증까지 완료하고 갱신된 `MeResponse`를 반환합니다.

인증 완료 후 사용자가 `PATCH /api/users/me/profile`에 `department` 필드를 포함해 수정하면 학생 인증 상태가 `REVOKED`로 변경됩니다. 프런트는 `REVOKED`일 때 **학생 인증이 취소되었습니다. 다시 인증을 진행하세요.** 안내와 재인증 버튼을 표시합니다. 기존 학교 토큰 확인 시각인 `schoolVerifiedAt`은 이력 확인을 위해 유지됩니다.

동일한 학교 학생은 하나의 축제 계정에만 연결할 수 있습니다. 가입 후 중복 연결이면 콜백 결과가 `?schoolVerification=already_linked`로 반환됩니다. 인증 완료 계정에는 학교 식별자 원문 대신 HMAC-SHA256 해시만 저장합니다. 관리자 확인이 필요한 미승인 요청은 비교를 위해 이름·학번·학과 원문을 별도 요청 테이블에 보관하고 승인 또는 삭제 시 제거합니다.

현재 미승인 요청에는 자동 만료와 정리 작업이 없으므로 관리자가 처리하지 않으면 원문 개인정보가 계속 보관됩니다. 프런트에서 요청이 자동 만료된다고 안내하면 안 됩니다.

### 학생 인증 승인 관리

이름 또는 학번이 다른 인증 요청은 `SUPER_ADMIN`만 조회·처리할 수 있습니다.

| Method | Path | 설명 |
|---|---|---|
| `GET` | `/api/admin/school-verifications` | 미승인 요청 목록 조회 |
| `POST` | `/api/admin/school-verifications/{id}/approve` | 요청 승인 및 학생 인증 완료 |
| `DELETE` | `/api/admin/school-verifications/{id}` | 미승인 요청 삭제 |

목록에는 현재 동아리 SSO의 이름·학번·학과와 학교 SSO의 이름·학번·학과가 함께 반환됩니다. `ADMIN`과 `STAFF`는 접근할 수 없습니다.

승인은 축제 서비스의 학생 인증 상태만 `VERIFIED`로 변경하며 동아리 SSO의 이름·학번·학과를 수정하지 않습니다. 필요한 회원정보 정정은 동아리 SSO에서 별도로 처리합니다. 현재 승인 API는 요청 생성 이후 사용자의 탈퇴·정지 여부와 학교 학적정보 변경 여부를 다시 확인하지 않으므로 운영자는 오래된 요청을 승인하지 않아야 합니다.

구현 세부사항과 현재 제약은 [`student-verification.md`](student-verification.md)를 기준으로 합니다.
