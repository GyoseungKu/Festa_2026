# 학생 인증 기능

이 문서는 Festa 2026에 구현된 삼육대학교 학생 인증 기능의 데이터 모델, 인증 흐름, API, 관리자 처리와 프런트 연동 규칙을 설명합니다.

## 1. 기능 범위

학생 인증은 동아리 SSO의 공통 인증 기능이 아니라 Festa 2026에서만 사용하는 축제 서비스 전용 상태입니다.

- 학교 SSO 학적정보로 가입한 사용자는 회원가입과 동시에 인증합니다.
- 직접 입력으로 가입한 기존 사용자는 로그인 후 학교 SSO 인증을 진행할 수 있습니다.
- 이름 또는 학번이 다르면 `SUPER_ADMIN` 승인을 받아야 합니다.
- 이름과 학번은 같고 학과만 다르면 사용자가 학교 학과로 변경하는 데 동의해야 합니다.
- 인증된 사용자가 본인 프로필에서 학과를 수정하면 인증을 취소합니다.
- 동일한 학교 학번은 하나의 축제 계정에만 연결할 수 있습니다.

## 2. 인증 상태

`festival_users.school_verification_status`는 다음 값을 가집니다.

| 상태 | 의미 |
|---|---|
| `UNVERIFIED` | 학교 학생 인증을 완료하지 않음 |
| `VERIFIED` | 학교 SSO 또는 관리자 승인을 통해 인증 완료 |
| `REVOKED` | 인증 후 사용자가 학과를 수정하여 인증 취소 |

내 정보와 운영자 사용자 조회 응답에는 다음 필드가 포함됩니다.

```json
{
  "schoolVerificationStatus": "VERIFIED",
  "schoolVerified": true,
  "schoolVerifiedAt": "2026-08-30T10:30:00Z"
}
```

- `schoolVerified`는 상태가 `VERIFIED`일 때만 `true`입니다.
- `REVOKED`로 변경해도 `schoolVerifiedAt`은 감사 이력을 위해 유지합니다.
- 프런트는 `REVOKED`일 때 `학생 인증이 취소되었습니다. 다시 인증을 진행하세요.` 안내와 재인증 버튼을 표시합니다.

## 3. 데이터 모델

### 3.1 festival_users

| 컬럼 | 용도 |
|---|---|
| `school_verification_status` | `UNVERIFIED`, `VERIFIED`, `REVOKED` 상태 |
| `school_verified_at` | 학교 SSO 토큰을 축제 서버가 확인한 시각. 관리자 승인 시에는 승인 요청을 만든 최초 콜백 시각이 유지됨 |
| `school_subject_hash` | 학교 학번의 HMAC-SHA256 해시, 사용자 간 유일 |

인증 완료 계정에는 학교 학번 원문을 축제 DB에 별도로 저장하지 않고 `school_subject_hash`만 저장합니다. 동아리 SSO의 공통 프로필에는 기존 정책에 따라 학번 원문이 존재할 수 있습니다.

### 3.2 school_verification_requests

이름 또는 학번이 일치하지 않아 관리자 확인이 필요한 요청을 저장합니다.

| 컬럼 | 용도 |
|---|---|
| `id` | 승인 요청 ID |
| `user_uuid` | 동아리 SSO 사용자 UUID, 사용자당 요청 하나 |
| `current_name` | 현재 동아리 SSO 이름 |
| `current_student_no` | 현재 동아리 SSO 학번 |
| `current_department` | 현재 동아리 SSO 학과 |
| `school_name` | 학교 SSO 이름 |
| `school_student_no` | 학교 SSO 학번 |
| `school_department` | 학교 SSO 학과 |
| `school_subject_hash` | 요청 시 계산한 학교 학번 해시. 현재 승인 구현은 이 값을 직접 사용하지 않고 학번 원문을 다시 해시함 |
| `school_verified_at` | 축제 서버가 학교 SSO 토큰을 검증한 시각 |
| `requested_at` | 관리자 승인 요청 시각 |

미승인 요청에는 관리자가 차이를 비교할 수 있도록 이름, 학번, 학과 원문을 저장합니다. 승인 또는 삭제하면 요청 행을 제거합니다. 같은 사용자가 다시 불일치 인증을 시도하면 기존 요청을 최신 정보로 갱신합니다.

현재 구현에는 미승인 요청의 만료 시각과 자동 정리 작업이 없습니다. 따라서 승인 또는 관리자의 수동 삭제 전까지 개인정보가 계속 보관됩니다. 운영 적용 전 요청 TTL과 만료 요청 정리 작업을 추가해야 하며, 보존기간은 개인정보 처리방침과 일치시켜야 합니다.

현재 프로젝트는 Hibernate `ddl-auto=update`를 사용하므로 엔티티를 기준으로 테이블이 생성됩니다. 운영 환경에서는 명시적 마이그레이션 도입을 권장합니다.

## 4. 회원가입과 동시에 인증

1. 사용자가 `GET /api/auth/school/authorize`로 학교 SSO 인증을 시작합니다.
2. 학교 SSO 콜백 검증이 성공하면 학적정보를 서버 세션에 임시 보관합니다.
3. 프런트는 `GET /api/auth/school/profile`로 이름, 학번, 학과를 조회합니다.
4. `POST /api/auth/signup`에 `academicInfoSource=SCHOOL_SSO`를 전송합니다.
5. 서버는 요청 본문의 이름·학번·학과를 신뢰하지 않고 세션의 검증값으로 강제 교체합니다.
6. 동아리 SSO 가입 성공 후 축제 사용자를 즉시 `VERIFIED`로 처리합니다.
7. 사용한 임시 학적정보를 세션에서 삭제합니다.

직접 입력 방식인 `MANUAL` 가입자는 `UNVERIFIED`로 생성됩니다.

현재 호출 순서는 동아리 SSO 회원가입 후 축제 DB에 학생 인증 상태를 저장하는 방식입니다. 동아리 SSO 가입은 성공했지만 축제 DB 저장이 실패하면 가입 API가 실패하더라도 동아리 SSO 계정은 이미 생성되어 있을 수 있습니다. 운영 환경에서는 `userUuid`를 기준으로 재처리할 수 있는 복구 작업과 사용자 안내가 필요합니다.

### 4.1 임시 학적정보 조회와 폐기

| Method | Path | 설명 |
|---|---|---|
| `GET` | `/api/auth/school/profile` | 회원가입 전에 세션에 보관된 학교 학적정보 조회 |
| `DELETE` | `/api/auth/school/profile` | 회원가입을 중단할 때 세션의 임시 학적정보 삭제 |

두 API 모두 학교 SSO 인증 과정에서 생성된 동일한 HTTP 세션을 사용해야 합니다. 임시 학적정보가 없거나 만료되면 `SCHOOL_SSO_VERIFICATION_REQUIRED` 오류가 반환됩니다.

## 5. 기존 회원 학생 인증

### 5.1 인증 시작

```http
POST /api/users/me/school-verification/authorize
Authorization: Bearer ACCESS_TOKEN
```

```json
{
  "authorizeUrl": "https://www.syu.ac.kr/festa-sso/authorize?..."
}
```

서버는 인증 시작 시 현재 동아리 SSO의 이름·학번·학과와 사용자 UUID를 세션의 OAuth state에 연결합니다. 프런트는 `authorizeUrl`로 브라우저를 이동합니다.

### 5.2 정보 비교

학교 SSO 콜백에서 다음 규칙으로 비교합니다.

- 이름: 앞뒤 공백을 제거한 뒤 완전 일치
- 학번: 공백과 하이픈을 제거한 뒤 완전 일치
- 학과: 앞뒤 공백을 제거한 뒤 완전 일치

| 비교 결과 | 처리 | 콜백 결과 |
|---|---|---|
| 이름·학번·학과 모두 일치 | 즉시 인증 | `schoolVerification=success` |
| 이름 또는 학번 불일치 | 관리자 승인 요청 생성 | `schoolVerification=pending_approval` |
| 이름·학번 일치, 학과 불일치 | 사용자 학과 변경 확인 대기 | `schoolVerification=department_update_required` |
| 동일 학교 학번이 다른 계정에 연결됨 | 인증 거절 | `schoolVerification=already_linked` |
| 사용자 취소 | 인증 중단 | `schoolVerification=access_denied` |
| 검증 실패 | 인증 실패 | `schoolVerification=failed` |
| state 만료·불일치 | 인증 실패 | `schoolVerification=invalid_state` |

콜백 결과는 `school-sso.return-url`로 리다이렉트하면서 쿼리 파라미터로 전달합니다.

세션에는 인증 state를 하나만 저장합니다. 같은 브라우저 세션에서 여러 탭이 동시에 인증을 시작하면 나중에 시작한 흐름이 앞선 state와 임시 프로필을 덮어쓰므로, 먼저 시작한 콜백은 `invalid_state`로 실패할 수 있습니다. 프런트는 인증 진행 중 인증 버튼을 비활성화하여 중복 시작을 방지해야 합니다.

## 6. 학과 불일치 처리

학과 불일치 결과를 받은 프런트는 다음 API로 비교 정보를 조회합니다.

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

권장 안내 문구:

> 학적정보와 회원정보가 일치하지 않습니다. 회원정보를 학교 학적정보를 기준으로 업데이트할까요?

사용자가 동의하면 요청 본문 없이 다음 API를 호출합니다.

```http
POST /api/users/me/school-verification/department/confirm
Authorization: Bearer ACCESS_TOKEN
```

서버는 프런트가 보낸 학과 문자열을 사용하지 않고 세션에 저장된 학교 학과로 동아리 SSO 프로필을 수정합니다. 수정 성공 후 학생 인증을 `VERIFIED`로 완료하고 갱신된 내 정보를 반환합니다.

동아리 SSO 학과 수정과 축제 DB 학생 인증 저장은 하나의 분산 트랜잭션이 아닙니다. 동아리 SSO 수정 후 축제 DB 저장이 실패하면 학과는 변경됐지만 인증이 완료되지 않을 수 있습니다. 클라이언트는 실패 시 학교 인증을 다시 진행할 수 있어야 하며, 서버에는 불일치 상태를 재처리하는 운영 절차가 필요합니다.

임시 학교 프로필은 인증을 시작한 사용자 UUID에 바인딩되며 기본 15분 후 만료됩니다.

## 7. 이름·학번 불일치와 관리자 승인

이름 또는 학번이 다르면 사용자 인증을 즉시 완료하지 않고 `school_verification_requests`에 요청을 생성합니다.

관리자 페이지:

```text
/admin/school-verifications
```

이 페이지는 `SUPER_ADMIN`만 접근할 수 있습니다. 동아리 SSO 정보와 학교 학적정보를 나란히 비교하고 승인 또는 삭제할 수 있습니다.

관리 API:

| Method | Path | 설명 |
|---|---|---|
| `GET` | `/api/admin/school-verifications` | 미승인 요청 목록 조회 |
| `POST` | `/api/admin/school-verifications/{id}/approve` | 요청 승인 및 학생 인증 완료 |
| `DELETE` | `/api/admin/school-verifications/{id}` | 요청 삭제, 사용자 계정은 유지 |

승인하면 요청에 저장된 학교 학번을 서버에서 다시 HMAC 해시하고 사용자를 `VERIFIED`로 처리한 뒤 요청 행을 삭제합니다. 승인 API 자체는 동아리 SSO의 이름, 학번 또는 학과를 수정하지 않습니다. 필요한 회원정보 정정은 관리자가 동아리 SSO에서 별도로 처리해야 합니다.

관리자가 동아리 SSO 정보를 직접 수정한 후 사용자가 인증을 다시 시도해 모든 정보가 일치하면 이전 미승인 요청은 자동 삭제되고 즉시 인증됩니다.

현재 승인 로직은 요청 생성 이후의 동아리 SSO 계정 상태나 최신 학교 학적정보를 다시 조회하지 않습니다. 또한 회원 탈퇴 API도 미승인 요청을 삭제하지 않습니다. 이 때문에 오래된 요청이나 탈퇴 계정의 요청이 승인될 수 있으므로 운영 적용 전 다음 보완이 필요합니다.

- 승인 요청 TTL 검사와 만료 요청 자동 삭제
- 회원 탈퇴 시 해당 사용자의 미승인 요청 삭제
- 승인 직전 동아리 SSO 계정의 존재 여부와 `ACTIVE` 상태 확인
- 필요하면 학교 SSO 재검증 또는 사용자 재인증 요구

## 8. 학과 수정에 따른 인증 취소

인증된 사용자가 다음 API에 `department` 필드를 포함하면 SSO 프로필 수정 성공 후 학생 인증을 `REVOKED`로 변경합니다.

```http
PATCH /api/users/me/profile
Authorization: Bearer ACCESS_TOKEN
Content-Type: application/json

{
  "department": "변경할 학과"
}
```

기존 학과와 같은 값을 보내더라도 `department` 수정 요청 자체가 발생하면 인증을 취소합니다. 전화번호, 학년, 재학 상태만 수정하는 경우에는 학생 인증 상태를 변경하지 않습니다.

학과 불일치 확인 API도 내부적으로 학과를 수정하지만, 수정 직후 학교 학적정보로 다시 검증하여 최종 상태는 `VERIFIED`가 됩니다.

일반 프로필 수정은 동아리 SSO 변경 성공 후 축제 DB의 인증을 취소합니다. 두 저장소 사이에는 분산 트랜잭션이 없으므로 축제 DB 장애가 발생하면 변경된 학과와 `VERIFIED` 상태가 일시적으로 함께 남을 수 있습니다. 이 경우 재인증 또는 관리자 정합성 복구가 필요합니다.

## 9. 운영자 사용자 조회

운영자용 QR 조회와 사용자 검색 응답에는 다음 필드가 포함됩니다.

- `schoolVerificationStatus`
- `schoolVerified`
- `schoolVerifiedAt`

| 권한 | 조회 방식 | 학생 인증 정보 |
|---|---|---|
| `BOOTH_MANAGER` | QR 사용자 조회 | 표시 |
| `STAFF` | QR 조회, 사용자 검색 | 표시 |
| `ADMIN` | QR 조회, 사용자 검색 | 표시 |
| `SUPER_ADMIN` | QR 조회, 사용자 검색 | 표시 |

`BOOTH_MANAGER`는 개인정보 정책상 사용자 디렉터리 검색은 할 수 없고 QR로 조회한 사용자의 인증 상태만 확인할 수 있습니다.

## 10. 보안 설계

- OAuth state는 암호학적 난수로 생성하고 1회만 사용합니다.
- state 기본 유효시간은 10분입니다.
- 학교 토큰은 RS256 서명, Issuer, Audience, Subject, 발급·만료 시각을 검증합니다.
- 학교 학번 Claim과 JWT Subject가 일치해야 합니다.
- 학교 학번 연결값은 `SYU_SSO_SUBJECT_HASH_SECRET`을 사용하는 HMAC-SHA256으로 저장합니다.
- `school_subject_hash`의 유일 제약으로 동일 학생의 다중 축제 계정 연결을 차단합니다.
- 임시 학적정보 응답과 인증 관련 응답에는 `Cache-Control: no-store`를 적용합니다.
- 관리자 승인 API와 페이지는 `SUPER_ADMIN`만 사용할 수 있습니다.
- 학교 SSO Client Secret과 학번 해시 Secret은 저장소에 커밋하지 않습니다.
- `SYU_SSO_SUBJECT_HASH_SECRET`이 비어 있으면 Client Secret을 대신 사용하며, 실제 해시 시점에 비밀키가 16바이트 미만이면 처리를 거부합니다.
- 현재 `FestivalUserService`는 모든 DB 무결성 예외를 `SCHOOL_IDENTITY_ALREADY_LINKED`로 변환합니다. 운영 안정성을 위해 실제 `school_subject_hash` 유일 제약 위반만 이 오류로 변환하도록 보완해야 합니다.
- 현재 미승인 요청의 `school_subject_hash`는 승인 시 사용되지 않습니다. 컬럼을 제거하거나 승인 시 저장된 값과 재계산한 값의 일치 여부를 검사해야 합니다.

## 11. 오류 처리

프런트는 오류 메시지 문자열이 아니라 `code`를 기준으로 처리합니다.

| 오류 코드 | 의미 | 권장 처리 |
|---|---|---|
| `SCHOOL_SSO_NOT_CONFIGURED` | 학교 SSO 설정 누락 또는 기능 비활성화 | 인증 버튼 비활성화, 운영자 확인 |
| `SCHOOL_SSO_CODE_INVALID` | 인증 코드가 만료됐거나 올바르지 않음 | 인증을 처음부터 다시 시작 |
| `SCHOOL_SSO_TOKEN_INVALID` | 학교 토큰 검증 실패 | 인증을 처음부터 다시 시작 |
| `SCHOOL_SSO_BAD_GATEWAY` | 학교 SSO 응답 형식 또는 공개키가 올바르지 않음 | 잠시 후 제한적으로 재시도 |
| `SCHOOL_SSO_UNAVAILABLE` | 학교 SSO 연결 실패 또는 타임아웃 | 잠시 후 제한적으로 재시도 |
| `SCHOOL_SSO_RATE_LIMITED` | 학교 SSO 요청 제한 | 즉시 반복하지 말고 대기 후 재시도 |
| `SCHOOL_SSO_STATE_INVALID` | state 불일치 또는 만료 | 인증을 처음부터 다시 시작 |
| `SCHOOL_SSO_VERIFICATION_REQUIRED` | 세션의 임시 학적정보가 없거나 만료됨 | 인증을 처음부터 다시 시작 |
| `SCHOOL_IDENTITY_ALREADY_LINKED` | 동일 학교 학번이 다른 축제 계정에 연결됨 | 사용자 재시도 금지, 관리자 문의 안내 |
| `SCHOOL_VERIFICATION_REQUEST_NOT_FOUND` | 승인 요청이 없거나 이미 처리됨 | 관리자 목록 새로고침 |

학교 SSO가 성공한 뒤 축제 DB 처리에서 실패한 요청은 단순 자동 반복 시 동아리 SSO 중복 가입 오류가 발생할 수 있습니다. 회원가입 복구는 반환되거나 확인된 `userUuid`를 기준으로 축제 회원 연결과 학생 인증 저장만 재처리해야 합니다.

## 12. 주요 환경 설정

| 환경변수 | 용도 |
|---|---|
| `SCHOOL_SSO_ENABLED` | 학교 SSO 기능 활성화 |
| `SYU_SSO_CLIENT_ID` | 학교 SSO Client ID |
| `SYU_SSO_CLIENT_SECRET` | 학교 SSO Client Secret |
| `SYU_SSO_AUTHORIZE_URL` | 학교 인증 화면 URL |
| `SYU_SSO_TOKEN_URL` | 인증 코드 교환 URL |
| `SYU_SSO_JWKS_URL` | RS256 공개키 조회 URL |
| `SYU_SSO_CALLBACK_URL` | 학교 SSO 콜백 URL |
| `SYU_SSO_ISSUER` | 허용 JWT Issuer |
| `SYU_SSO_AUDIENCE` | 허용 JWT Audience |
| `SYU_SSO_RETURN_URL` | 인증 후 프런트 복귀 URL |
| `SYU_SSO_SUBJECT_HASH_SECRET` | 학교 학번 HMAC 비밀키 |

기본 시간 설정:

| 설정 | 기본값 |
|---|---|
| 연결 타임아웃 | 3초 |
| 읽기 타임아웃 | 5초 |
| state TTL | 10분 |
| 임시 프로필 TTL | 15분 |
| JWKS 캐시 | 1시간 |
| 허용 시각 오차 | 30초 |
| 학교 토큰 최대 발급 경과 시간 | 10분 |

## 13. 현재 구현 제약과 보완 우선순위

다음 항목은 현재 동작을 설명하는 것이 아니라 운영 적용 전에 보완해야 하는 사항입니다.

1. 미승인 요청 TTL, 자동 삭제 및 회원 탈퇴 연계
2. 승인 직전 계정 상태와 요청 최신성 검증
3. 동아리 SSO 성공 후 축제 DB 실패에 대한 재처리 작업
4. `school_verified_at`과 별도의 관리자 승인 시각이 필요할지 결정
5. 미승인 요청의 사용되지 않는 `school_subject_hash` 정리
6. DB 유일 제약 위반을 정확하게 식별하는 예외 처리
7. 동일 회원이 다른 학교 학번으로 재연결되는 것을 허용할지 정책 확정
8. 동시에 여러 인증을 시작할 수 있도록 state별 세션 저장을 지원할지 결정

## 14. 테스트 범위

현재 테스트는 학교 가입 정보 강제 적용, 기존 회원 일치 인증, 이름·학번 불일치 요청 생성, 학과 불일치 확인, 잘못된 state, 토큰 검증, 인증 취소와 운영자 조회 필드를 다룹니다.

추가해야 하는 테스트:

- `SUPER_ADMIN` 승인·삭제 API 성공과 권한 거부
- 만료된 승인 요청과 탈퇴·정지 사용자 승인 거부
- 승인 시 동일 학교 학번 충돌
- 동아리 SSO 성공 후 축제 DB 저장 실패 및 재처리
- `REVOKED` 사용자의 재인증
- 같은 학과 값을 다시 보낸 경우에도 인증 취소
- 동시에 여러 탭에서 인증을 시작한 경우

## 15. 관련 코드와 문서

- 학교 SSO 흐름: `schoolsso/SchoolSsoController.java`
- 학교 토큰 검증: `schoolsso/SchoolSsoClient.java`
- 세션과 state: `schoolsso/SchoolSsoSessionStore.java`
- 축제 사용자 인증 상태: `user/FestivalUser.java`
- 미승인 요청 처리: `user/SchoolVerificationApprovalService.java`
- 관리자 API: `user/SchoolVerificationAdminController.java`
- 관리자 페이지: `admin/AdminSchoolVerificationPageController.java`
- 프런트 사용자 API: [`frontend-user-api.md`](frontend-user-api.md)
- 운영자 QR·검색 API: [`frontend-qr-api.md`](frontend-qr-api.md)
