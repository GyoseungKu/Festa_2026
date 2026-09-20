# 프런트엔드 학교 SSO 연동 가이드

[문서 목차](README.md) · [프런트 연동 시작하기](frontend-getting-started.md)

**이 문서의 순서**

- [1. 용어와 기능 범위](#1-용어와-기능-범위)
- [2. 공통 구현 원칙](#2-공통-구현-원칙)
- [3. 전체 흐름 구분](#3-전체-흐름-구분)
- [4. 신규 회원가입에서 학교 SSO 사용](#4-신규-회원가입에서-학교-sso-사용)
- [5. 로그인 후 마이페이지에서 학교 연동](#5-로그인-후-마이페이지에서-학교-연동)
- [6. callback 전용 프런트 라우트 예시](#6-callback-전용-프런트-라우트-예시)
- [7. 세션·쿠키·로컬 개발 주의사항](#7-세션쿠키로컬-개발-주의사항)
- [8. 오류 코드](#8-오류-코드)
- [9. 프런트 구현 체크리스트](#9-프런트-구현-체크리스트)
- [10. 관련 문서](#10-관련-문서)

이 문서는 Festa 프런트엔드에서 삼육대학교 학교 SSO를 연동하는 방법을 설명합니다. 공통 `fetch`, 쿠키, Access Token 처리는 [공통 API 규약](frontend-api-common.md)을 먼저 확인합니다.

## 1. 용어와 기능 범위

이 프로젝트에는 역할이 다른 두 인증 체계가 있습니다.

| 구분 | 용도 |
|---|---|
| Likelion SSO | Festa 계정의 회원가입, 아이디·비밀번호 로그인, Access/Refresh Token 발급 |
| 학교 SSO | 삼육대학교 학번·학과·이름 확인과 Festa 학생 인증 |

학교 SSO는 Festa 로그인 수단이 아닙니다. 학교 SSO 인증을 완료해도 Festa Access Token이 발급되지 않으며, 신규 사용자는 Likelion SSO 회원가입을 완료한 뒤 별도로 로그인해야 합니다.

프런트에서 구현할 흐름은 두 가지입니다.

1. 신규 회원가입 중 학교 SSO에서 학적정보를 불러오기
2. 이미 로그인한 회원이 마이페이지에서 학교 학생 인증 연결하기

## 2. 공통 구현 원칙

- 모든 Festa API 요청에 `credentials: "include"`를 사용합니다.
- 학교 인증 화면은 팝업보다 현재 창 전체 이동을 권장합니다.
- `state`, Authorization Code, 학교 JWT를 프런트에서 만들거나 처리하지 않습니다.
- 학교 SSO 시작부터 callback 완료까지 같은 브라우저 세션을 유지합니다.
- 인증 버튼을 연속 클릭하거나 여러 탭에서 동시에 인증을 시작하지 못하게 합니다.
- callback 결과 쿼리는 처리 후 주소에서 제거합니다.
- 학번·이름·학과를 URL, analytics, 오류 로그에 기록하지 않습니다.
- 학교에서 받은 학적정보는 `localStorage`에 보관하지 않습니다.

운영 callback 주소는 다음과 같습니다.

```text
https://festa.syu-likelion.org/auth/sso/callback
```

callback 처리 후 브라우저는 프런트 경로로 돌아옵니다.

```text
https://festa.syu-likelion.org/auth/school/result
```

인증 결과 화면은 React가 제공합니다. `SYU_SSO_RETURN_URL`을 해당 화면의 절대 HTTPS URL로 설정하세요. 기본 경로는 `/auth/school/result`입니다. 기존 배포 설정이 예전 테스트 화면을 가리키면 이 값도 변경해야 합니다.

학교에 등록된 `/auth/sso/callback`은 백엔드로, `/auth/school/result`는 React로 전달합니다. `/auth/**` 전체를 백엔드로 전달하지 않습니다. `SCHOOL_SSO_ENABLED=true`와 학교 발급 자격증명은 실제 연동에 계속 필요합니다.

## 3. 전체 흐름 구분

### 3.1 신규 회원가입

```text
회원가입 화면
→ 학적정보 입력 방식에서 학교 SSO 선택
→ GET /api/auth/school/authorize 로 브라우저 이동
→ 학교 로그인 및 개인정보 제공 동의
→ 학교가 Festa callback 호출
→ Festa가 state, code, JWT 검증
→ /auth/school/result?schoolSso=success
→ GET /api/auth/school/profile
→ 검증된 이름·학번·학과 표시
→ POST /api/auth/signup (academicInfoSource=SCHOOL_SSO)
→ 가입 완료
→ Festa 로그인 화면
```

### 3.2 로그인 후 학생 인증

```text
마이페이지
→ POST /api/users/me/school-verification/authorize
→ 응답 authorizeUrl로 브라우저 이동
→ 학교 로그인 및 개인정보 제공 동의
→ 학교가 Festa callback 호출
→ 기존 회원정보와 학교정보 비교
→ /auth/school/result?schoolVerification=<결과>
→ 결과별 UI 처리
```

## 4. 신규 회원가입에서 학교 SSO 사용

### 4.1 입력 방식 선택 UI

회원가입 화면에서 다음 두 방식을 선택하게 합니다.

| 값 | UI |
|---|---|
| `MANUAL` | 이름·학번·학과 직접 입력 |
| `SCHOOL_SSO` | `학교 SSO에서 불러오기` 버튼 표시, 인증 후 필드를 읽기 전용으로 표시 |

학교 프로필은 기본 15분만 유효하므로 다음 순서를 권장합니다.

```text
아이디·이메일 등 입력
→ 이메일 인증 완료
→ 학교 SSO 인증
→ 학적정보 확인
→ 즉시 회원가입 요청
```

사용자가 학교 인증을 시작하면 현재 페이지를 벗어나므로 작성 중이던 비민감 필드가 필요하다면 `sessionStorage` 등에 임시 저장할 수 있습니다. 비밀번호와 인증번호는 저장하지 않습니다.

### 4.2 학교 인증 시작

이 API는 JSON을 받는 AJAX API가 아니라 학교 페이지로 보내는 리다이렉트 엔드포인트입니다.

```http
GET /api/auth/school/authorize
```

```ts
export function startSchoolSsoForSignup() {
  const apiBase = import.meta.env.VITE_API_BASE_URL ?? "";
  window.location.assign(`${apiBase}/api/auth/school/authorize`);
}
```

`fetch()`로 호출한 뒤 응답을 파싱하지 마십시오. 브라우저 자체를 위 주소로 이동시켜야 합니다.

### 4.3 callback 결과 처리

신규 회원가입 흐름은 `schoolSso` 쿼리를 사용합니다.

| 값 | 의미 | 프런트 처리 |
|---|---|---|
| `success` | 학교 토큰과 학적정보 검증 완료 | 임시 학적정보 조회 |
| `access_denied` | 사용자가 정보 제공 거부 | 회원가입 화면으로 복귀, 직접 입력 선택 제공 |
| `invalid_state` | 세션 불일치, 중복 시작 또는 state 만료 | 처음부터 다시 인증 안내 |
| `failed` | Code 교환 또는 JWT 검증 실패 | 재시도 안내 |

```ts
type SignupSchoolSsoResult =
  | "success"
  | "access_denied"
  | "invalid_state"
  | "failed";

const params = new URLSearchParams(window.location.search);
const result = params.get("schoolSso") as SignupSchoolSsoResult | null;
```

결과를 읽은 뒤 쿼리에 민감정보는 없더라도 새로고침 재처리를 막기 위해 제거합니다.

```ts
window.history.replaceState({}, "", window.location.pathname);
```

### 4.4 검증된 학적정보 조회

`schoolSso=success`일 때 같은 브라우저에서 즉시 호출합니다.

```http
GET /api/auth/school/profile
```

```ts
export type SchoolSignupProfile = {
  studentNo: string;
  department: string;
  name: string;
  consentTarget: string;
  expiresAt: string;
};
```

```json
{
  "studentNo": "20260001",
  "department": "컴퓨터공학과",
  "name": "홍길동",
  "consentTarget": "2026학년도 총학생회",
  "expiresAt": "2026-09-02T06:15:00Z"
}
```

- 응답 필드는 학교 SSO 검증 결과이므로 수정할 수 없는 필드로 표시합니다.
- `expiresAt` 이후에는 회원가입을 진행하지 말고 학교 인증을 다시 시작합니다.
- 기본 임시 프로필 유효시간은 검증 완료 후 15분입니다.
- 세션이 없거나 만료되면 `400 SCHOOL_SSO_VERIFICATION_REQUIRED`입니다.

### 4.5 학교 방식 회원가입 요청

```http
POST /api/auth/signup
Content-Type: application/json
```

```json
{
  "loginId": "festival01",
  "password": "Password123!",
  "email": "student@example.com",
  "name": "홍길동",
  "phone": null,
  "studentNo": "20260001",
  "department": "컴퓨터공학과",
  "grade": null,
  "enrollment": null,
  "birthDate": null,
  "academicInfoSource": "SCHOOL_SSO"
}
```

> `SCHOOL_SSO` 방식에서도 `name`, `studentNo`, `department`를 빈 값이나 `null`로 보내면 안 됩니다. 요청 DTO 검증을 통과하도록 `GET /api/auth/school/profile`에서 받은 값을 그대로 전송하십시오. 서버는 최종적으로 요청값을 신뢰하지 않고 세션의 검증값으로 다시 교체합니다.

가입 성공 응답은 다음과 같으며 자동 로그인되지는 않습니다.

```json
{
  "userUuid": "123e4567-e89b-12d3-a456-426614174000"
}
```

가입 성공 후 로그인 화면으로 이동하고, 사용자가 생성한 Festa 아이디와 비밀번호로 로그인하게 합니다.

회원가입 성공 시 임시 학교 프로필은 서버에서 즉시 삭제됩니다. 가입 요청이 실패하면 오류에 따라 다시 시도할 수 있지만 `SCHOOL_SSO_VERIFICATION_REQUIRED`이면 학교 인증부터 다시 시작합니다.

### 4.6 학교 방식 취소

사용자가 학교 방식에서 직접 입력 방식으로 전환하거나 가입을 중단하면 임시 학적정보를 삭제합니다.

```http
DELETE /api/auth/school/profile
```

성공은 `204 No Content`입니다.

## 5. 로그인 후 마이페이지에서 학교 연동

### 5.1 현재 인증 상태 표시

먼저 내 정보를 조회합니다.

```http
GET /api/users/me
Authorization: Bearer ACCESS_TOKEN
```

관련 필드:

```ts
type SchoolVerificationStatus = "UNVERIFIED" | "VERIFIED" | "REVOKED";

type SchoolVerificationFields = {
  schoolVerificationStatus: SchoolVerificationStatus;
  schoolVerified: boolean;
  schoolVerifiedAt: string | null;
};
```

| 상태 | 권장 UI |
|---|---|
| `UNVERIFIED` | `학교 학생 인증하기` 버튼 |
| `VERIFIED` | 인증 완료 표시와 인증 시각 |
| `REVOKED` | 학생 인증이 취소되었다는 안내와 `다시 인증하기` 버튼 (학과 변경 또는 관리자 회수) |

`schoolVerified`는 상태가 `VERIFIED`이고 `schoolVerifiedAt`이 존재할 때만 `true`입니다.

### 5.2 인증 URL 발급

```http
POST /api/users/me/school-verification/authorize
Authorization: Bearer ACCESS_TOKEN
```

요청 본문은 없습니다.

```json
{
  "authorizeUrl": "https://www.syu.ac.kr/festa-sso/authorize?..."
}
```

```ts
type SchoolAuthorizationResponse = {
  authorizeUrl: string;
};

export async function startSchoolVerification() {
  const response = await apiFetch<SchoolAuthorizationResponse>(
    "/api/users/me/school-verification/authorize",
    { method: "POST", auth: true },
  );
  window.location.assign(response.authorizeUrl);
}
```

응답의 URL을 프런트에서 조립하거나 `state`를 수정하지 않습니다. API가 `X-Access-Token`을 반환하면 공통 요청 래퍼가 이동 전에 새 Access Token으로 교체해야 합니다.

### 5.3 callback 결과 처리

로그인 후 연동 흐름은 `schoolVerification` 쿼리를 사용합니다.

| 값 | 의미 | 프런트 처리 |
|---|---|---|
| `success` | 기존 회원정보와 학교정보가 모두 일치하고 인증 완료 | `/api/users/me` 재조회 |
| `pending_approval` | 이름 또는 학번 불일치 | 관리자 승인 대기 안내 |
| `department_update_required` | 이름·학번은 같지만 학과가 다름 | 학과 비교·확인 화면 표시 |
| `already_linked` | 같은 학교 학번이 다른 Festa 계정에 연결됨 | 재시도 중단, 관리자 문의 안내 |
| `access_denied` | 사용자가 정보 제공 거부 | 취소 안내 |
| `invalid_state` | 세션 불일치, 중복 시작 또는 만료 | 처음부터 다시 인증 안내 |
| `failed` | Code 교환, JWT 검증 또는 서버 처리 실패 | 제한적인 재시도 안내 |

```ts
type SchoolVerificationResult =
  | "success"
  | "pending_approval"
  | "department_update_required"
  | "already_linked"
  | "access_denied"
  | "invalid_state"
  | "failed";
```

`success` 처리 후에는 반드시 `/api/users/me`를 다시 조회하고 전역 사용자 상태를 교체합니다.

`pending_approval` 상태를 일반 사용자가 별도로 조회하는 전용 API는 없습니다. callback에서 승인 대기 메시지를 보여주고, 이후 마이페이지 진입이나 새로고침 때 `/api/users/me`의 `schoolVerificationStatus`를 다시 확인합니다. 승인 전에는 이전 상태(`UNVERIFIED` 또는 `REVOKED`)가 유지됩니다.

### 5.4 학과 불일치 확인

`department_update_required`일 때 같은 로그인·브라우저 세션에서 호출합니다.

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

권장 안내:

> 현재 회원정보의 학과를 학교 SSO에서 확인한 학과로 변경하고 학생 인증을 완료할까요?

사용자가 동의하면 요청 본문 없이 호출합니다.

```http
POST /api/users/me/school-verification/department/confirm
Authorization: Bearer ACCESS_TOKEN
```

성공 응답은 변경된 전체 `MeResponse`입니다. 별도 재조회 대신 이 응답으로 전역 사용자 상태를 교체할 수 있습니다.

사용자가 변경을 취소하면 확인 화면을 닫고 필요하면 다음 API로 세션의 임시 학교정보를 삭제합니다.

```http
DELETE /api/auth/school/profile
```

임시 학교 프로필은 기본 15분 뒤 자동으로 사용할 수 없게 됩니다.

### 5.5 인증 후 프로필 변경

인증된 사용자가 `PATCH /api/users/me/profile` 요청에 `department` 필드를 포함하면 학생 인증 상태가 `REVOKED`로 변경됩니다. 기존 값과 같은 학과를 전송해도 학과 수정 요청 자체가 있으면 취소됩니다.

전화번호, 학년, 재학 상태만 변경하면 인증 상태는 유지됩니다.

## 6. callback 전용 프런트 라우트 예시

하나의 `/auth/school/result` 라우트에서 두 쿼리를 구분합니다.

```ts
async function handleSchoolSsoCallback() {
  const params = new URLSearchParams(window.location.search);
  const signupResult = params.get("schoolSso");
  const verificationResult = params.get("schoolVerification");

  window.history.replaceState({}, "", window.location.pathname);

  if (signupResult === "success") {
    const profile = await apiFetch<SchoolSignupProfile>(
      "/api/auth/school/profile",
    );
    // 회원가입 store에 profile 저장 후 회원가입 화면으로 이동
    return;
  }

  if (verificationResult === "success") {
    const me = await apiFetch<MeResponse>("/api/users/me", { auth: true });
    // 사용자 store 교체 후 마이페이지로 이동
    return;
  }

  if (verificationResult === "department_update_required") {
    // 학과 비교 화면으로 이동
    return;
  }

  // 나머지 결과는 코드별 안내 후 회원가입 또는 마이페이지로 이동
}
```

라우트 진입 시 두 쿼리가 모두 없으면 잘못된 직접 접근으로 보고 회원가입 또는 마이페이지로 이동합니다.

로그인 후 callback에서 메모리 Access Token이 사라질 수 있는 SPA 구조라면 Refresh 쿠키로 다음 API를 먼저 호출해 Access Token을 복구합니다.

```http
POST /api/auth/token/refresh
```

그 다음 `/api/users/me` 또는 학과 불일치 API를 호출합니다.

## 7. 세션·쿠키·로컬 개발 주의사항

학교 SSO의 `state`와 임시 학적정보는 Festa 서버의 `JSESSIONID` 세션에 저장됩니다.

- `credentials: "include"`가 빠지면 callback 이후 프로필을 찾지 못할 수 있습니다.
- 브라우저 쿠키를 삭제하거나 다른 브라우저에서 callback URL을 열면 `invalid_state`가 됩니다.
- 뒤로가기 후 이전 callback URL을 다시 열어도 state가 이미 폐기되어 실패합니다.
- 같은 세션에서 새 인증을 시작하면 이전 state를 덮어씁니다.
- `GET /api/auth/school/authorize`를 API 테스트 도구에서 호출한 뒤 callback만 브라우저에서 열면 세션이 달라 실패합니다.

로컬 프런트 개발에서는 같은 Origin 프록시 사용을 권장합니다. 운영 학교 callback URI가 고정되어 있으므로 실제 학교 인증 완료 후에는 운영 Festa 도메인으로 돌아옵니다. 로컬 화면만으로 end-to-end callback을 처리하려면 학교 측에 별도 개발 callback URI 등록이 필요합니다.

## 8. 오류 코드

callback 내부의 Code/JWT 오류는 대부분 `schoolSso=failed` 또는 `schoolVerification=failed`로 일반화됩니다. 아래 API 오류는 직접 API 호출 중 처리합니다.

| HTTP | code | 발생 위치 | 처리 |
|---|---|---|---|
| `400` | `SCHOOL_SSO_VERIFICATION_REQUIRED` | 학적정보 조회·가입·학과 확인 | 학교 인증 다시 시작 |
| `400` | `SCHOOL_SSO_CODE_INVALID` | callback 내부 | 일반적으로 `failed` 쿼리로 수신 |
| `400` | `SCHOOL_SSO_TOKEN_INVALID` | callback 내부 | 일반적으로 `failed` 쿼리로 수신 |
| `401` | `UNAUTHORIZED` | 로그인 후 연동 API | 토큰 복구 또는 재로그인 |
| `409` | `SCHOOL_IDENTITY_ALREADY_LINKED` | 학교정보 저장 | callback에서는 `already_linked`로 수신 |
| `429` | `SCHOOL_SSO_RATE_LIMITED` | 학교 연동 | 잠시 후 재시도 |
| `502` | `SCHOOL_SSO_BAD_GATEWAY` | 학교 응답 오류 | 잠시 후 재시도 |
| `503` | `SCHOOL_SSO_NOT_CONFIGURED` | 인증 시작 | 인증 버튼 비활성화, 운영자 확인 |
| `503` | `SCHOOL_SSO_UNAVAILABLE` | 학교 연결 실패 | 잠시 후 재시도 |

오류 메시지 문자열이 아니라 `code`와 callback 결과값으로 분기합니다.

## 9. 프런트 구현 체크리스트

### 신규 회원가입

- [ ] `MANUAL`/`SCHOOL_SSO` 선택 UI
- [ ] 학교 인증 시작은 `window.location.assign()` 사용
- [ ] `/auth/school/result?schoolSso=...` 라우트 처리
- [ ] 성공 후 `/api/auth/school/profile` 조회
- [ ] 학교 필드는 읽기 전용 표시
- [ ] 가입 요청에도 학교 프로필의 세 필드를 그대로 포함
- [ ] `academicInfoSource=SCHOOL_SSO` 설정
- [ ] 취소 시 임시 프로필 삭제
- [ ] 가입 성공 후 로그인 화면 이동

### 로그인 후 연동

- [ ] `/api/users/me`에서 학생 인증 상태 표시
- [ ] 인증 URL 발급 POST에 Bearer와 쿠키 포함
- [ ] 응답의 `authorizeUrl`로 이동
- [ ] `/auth/school/result?schoolVerification=...` 결과 전체 처리
- [ ] 성공 후 내 정보 갱신
- [ ] 학과 불일치 비교·확인 UI
- [ ] 승인 대기 안내
- [ ] `REVOKED` 재인증 UI

### 공통

- [ ] SSO 진행 중 버튼 중복 실행 방지
- [ ] callback 결과 처리 후 쿼리 제거
- [ ] Access Token과 `X-Access-Token` 갱신 처리
- [ ] 모든 API에 `credentials: "include"`
- [ ] 개인정보·code·token을 URL이나 로그에 저장하지 않음

## 10. 관련 문서

- [공통 API 규약](frontend-api-common.md)
- [인증·회원가입 API](frontend-auth-api.md)
- [내 정보·계정 API](frontend-user-api.md)
- [학생 인증 백엔드·운영 상세](student-verification.md)


## 학교 학과명 통일 규칙

학교 SSO 학적정보는 백엔드에서 아래 규칙으로 학과명을 변환한 뒤 세션에 보관합니다. 학적정보 조회·회원가입·기존 회원의 학과 비교·학과 변경 확인·관리자 승인에는 변환된 값이 사용됩니다.

| 학교 SSO 학과 값 | 서비스 학과 값 |
|---|---|
| 인공지능공학전공, 지능형반도체전공, 경영정보시스템전공 | 인공지능융합학부 |
| 컴퓨터공학전공, 소프트웨어전공 | 컴퓨터공학부 |
| 항공관광전공, 동양어문화전공 | 항공관광외국어학부 |
| `자유전공`이 포함된 모든 값 | 자유전공학부 |
| `건축학과(4년제)`, `건축학과(5년제)` 등 숫자 연제 표기 | 건축학과 |

앞뒤 공백은 제거하며 건축학과와 괄호·연제 사이의 공백도 허용합니다. 목록에 없는 학과명은 유지합니다. 예를 들어 `컴퓨터공학과`를 임의로 `컴퓨터공학부`로 바꾸지는 않습니다.

프런트가 `컴퓨터공학부`를 저장한 회원에게 학교가 `컴퓨터공학전공`을 반환하면, 학과가 일치하는 것으로 비교합니다. 기존 회원 DB의 학과값이나 수동 입력값을 일괄 변경하는 작업은 포함하지 않습니다.
