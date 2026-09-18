# 동적 사용자 QR API

[문서 목차](README.md) · [프런트 연동 시작하기](frontend-getting-started.md)

**이 문서의 순서**

- [내 QR 토큰 발급](#내-qr-토큰-발급)
- [일반 QR 사용자 조회](#일반-qr-사용자-조회)
- [사용자 정보 검색](#사용자-정보-검색)
- [관리자 전체 회원 목록](#관리자-전체-회원-목록)
- [사용자 관리 권한 변경](#사용자-관리-권한-변경)
- [사용자 학생 인증 변경](#사용자-학생-인증-변경)
- [오류](#오류)

이 문서는 사용자가 자신의 QR을 표시하는 기능과 운영자가 일반 사용자 정보를 조회하는 API를 설명합니다. 부스 스탬프 지급은 [스탬프 API](frontend-stamps-api.md)를 사용합니다.

## 내 QR 토큰 발급

```http
POST /api/qr/tokens
Authorization: Bearer ACCESS_TOKEN
```

성공 `200`:

```json
{
  "token": "temporary-random-token",
  "expiresAt": "2026-08-18T03:01:00Z"
}
```

- QR 이미지에는 응답의 `token` 문자열만 넣습니다.
- Access Token, `userUuid`, 이름이나 학번을 QR에 넣지 않습니다.
- 기본 유효시간은 60초입니다. `expiresAt` 전에 새 토큰을 발급하고 QR 이미지를 교체합니다.
- 새 토큰 발급이 기존 토큰을 즉시 폐기하지는 않습니다. 각 토큰은 자신의 만료 시각까지 유효합니다.
- 토큰을 URL query, analytics, 콘솔이나 영구 저장소에 기록하지 않습니다.

```tsx
const [qr, setQr] = useState<{ token: string; expiresAt: string } | null>(null);

useEffect(() => {
  let timer: number | undefined;
  let stopped = false;
  const refresh = async () => {
    try {
      const next = await apiFetch<{ token: string; expiresAt: string }>("/api/qr/tokens", {
        method: "POST",
        auth: true,
      });
      if (stopped) return;
      setQr(next);
      const delay = Math.max(5_000, new Date(next.expiresAt).getTime() - Date.now() - 5_000);
      timer = window.setTimeout(refresh, delay);
    } catch (error) {
      if (stopped) return;
      setQr(null); // 갱신에 실패한 QR을 계속 보여주지 않음
      // 오류 안내와 사용자 재시도 버튼을 표시하고, 401은 공통 로그인 처리로 전달
    }
  };
  void refresh();
  return () => {
    stopped = true;
    window.clearTimeout(timer);
  };
}, []);
```

## 관리자 전체 회원 목록

전체 목록은 관리자 HTML의 `GET /admin/qr/users`에서 제공합니다. `/admin/qr`의 **전체 회원 조회·필터** 버튼으로 이동합니다. 기존 `/api/qr/search` 요청 형식은 바뀌지 않으며 빈 검색어를 전체 목록 조회로 사용하지 않습니다.

- ADMIN·SUPER_ADMIN만 전체 목록에 접근할 수 있습니다. 기본 100명·최대 100명씩 조회하며 화면에서 20·50·100명을 선택할 수 있습니다.
- 축제 서비스 등록 순서의 역순으로 표시하고 학생 인증·학생회비 확인 상태로 필터링합니다. ADMIN 이상은 축제 권한도 필터링할 수 있습니다.
- STAFF 이하의 전체 목록 요청은 서버에서 403으로 거부합니다. STAFF의 기존 검색 조회와 이름·학번 마스킹은 유지하며 BOOTH_MANAGER의 기존 QR 조회 권한도 유지합니다.
- 축제 DB에 연결된 회원만 대상으로 합니다. SSO 전체 가입자나 이미 축제 서비스를 탈퇴해 연결이 삭제된 사용자는 포함하지 않습니다.
- 전체 UUID·프로필을 불러온 뒤 화면에서 나누지 않습니다. DB 페이지에 포함된 프로필·팔찌 상태만 일괄 조회합니다.
- Bearer 기반 사용자 API가 아닌 관리자 쿠키 기반 HTML 응답입니다. 정확한 필터 파라미터는 [관리자 웹 문서](admin-web-api.md)를 확인합니다.

## 일반 QR 사용자 조회

```http
POST /api/qr/scan
Authorization: Bearer OPERATOR_ACCESS_TOKEN
Content-Type: application/json

{ "token": "scanned-token" }
```

`USER`는 사용할 수 없습니다. 응답은 조회자의 최고 축제 권한에 따라 필드가 제한되며 `null` 필드는 JSON에서 생략될 수 있습니다.

| 조회 권한 | 제공 범위 |
|---|---|
| `BOOTH_MANAGER` | 마스킹 이름·학번, 학과, 학년 |
| `STAFF` | 마스킹 이름·학번, 학과, 학년 + 학생 인증 상태·시각·학생회비 납부 여부 |
| `ADMIN` | 원본 이름·학번, 학과, 학년 + 전화번호, 이메일 + 학생 인증 상태·시각·학생회비 납부 여부 |
| `SUPER_ADMIN` | SSO 전체 프로필 + 축제 권한 + 학생 인증 상태·시각·학생회비 납부 여부 |

`STAFF`, `ADMIN`, `SUPER_ADMIN` 조회 응답에만 다음 학생 인증·납부 필드가 포함됩니다. `BOOTH_MANAGER` 단독 권한에는 네 필드 모두 JSON에서 생략되며 관리자 화면에도 표시되지 않습니다. 프런트는 필드 미제공을 미인증·미납부로 해석하지 말고 해당 항목을 숨겨야 합니다.

```ts
type SchoolVerificationFields = {
  schoolVerificationStatus: "UNVERIFIED" | "VERIFIED" | "REVOKED";
  schoolVerified: boolean;
  schoolVerifiedAt?: string; // 인증 시각이 없으면 생략
  studentFeePaid: boolean; // 미인증은 항상 false; UI에서는 학생인증 필요로 표시
};
```

이 API는 일반 정보 조회용입니다. 스탬프 지급 화면에서는 `/api/booths/{boothId}/stamps/qr/lookup`을 사용해야 담당 부스와 `stampEnabled`가 검증됩니다.

## 사용자 정보 검색

```http
POST /api/qr/search
Authorization: Bearer OPERATOR_ACCESS_TOKEN
Content-Type: application/json

{ "query": "컴퓨터공학과", "page": 0, "size": 20 }
```

- `STAFF`, `ADMIN`, `SUPER_ADMIN`만 사용할 수 있습니다. `BOOTH_MANAGER`는 기존 QR 조회만 가능합니다.
- 검색어는 2–100자이며 이름, 학번, 로그인 ID, 이메일, 전화번호, 학과, 재학 상태, 사용자 UUID의 원본 정보와 비교합니다.
- 원본 정보로 검색하더라도 결과 필드는 위 권한 표에 따라 마스킹·제한됩니다. 예를 들어 STAFF가 `홍길동`으로 검색해도 결과 이름은 `홍*동`입니다.
- 축제 서비스에 한 번 이상 연동된 사용자만 검색 대상입니다.
- `page`는 0부터 시작하고 기본 페이지 크기는 20명입니다. `size`는 1–100 범위이며, 요청 페이지가 마지막 페이지보다 크면 마지막 페이지를 반환합니다.
- 이름처럼 결과가 적은 검색뿐 아니라 학과처럼 결과가 많은 검색도 `totalPages`를 기준으로 페이지 버튼을 구성하십시오. 페이지 이동 시 같은 `query`와 원하는 `page`를 다시 전송합니다.

```ts
type UserSearchResponse = {
  items: QrUserView[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};
```

`ADMIN` 이상 검색 결과에는 권한 변경에 필요한 `userUuid`, `festivalRoles`가 포함됩니다. `managementRole()`은 서버 내부 계산 메서드이며 현재 JSON 필드가 아닙니다. 프런트는 `festivalRoles`에서 `SUPER_ADMIN → ADMIN → STAFF → USER` 순으로 관리 역할을 계산합니다. `BOOTH_MANAGER`는 별도로 판단합니다.

### QR·검색 사용자 응답 타입

`POST /api/qr/scan`은 `QrUserView` 객체, 검색은 `items: QrUserView[]`를 반환합니다. 아래 선택 필드는 권한 또는 프로필 누락에 따라 JSON에서 생략됩니다. `viewerRole`은 대상 사용자가 아니라 조회자의 역할입니다.

```ts
type QrUserView = {
  viewerRole: "BOOTH_MANAGER" | "STAFF" | "ADMIN" | "SUPER_ADMIN";
  userUuid?: string;
  loginId?: string;
  email?: string;
  ssoRole?: string;
  status?: string;
  name?: string;
  phone?: string;
  studentNo?: string;
  department?: string;
  grade?: number;
  enrollment?: string;
  birthDate?: string;
  createdAt?: string;
  updatedAt?: string;
  festivalRoles?: Array<"USER" | "STAFF" | "ADMIN" | "SUPER_ADMIN" | "BOOTH_MANAGER">;
  schoolVerificationStatus?: "UNVERIFIED" | "VERIFIED" | "REVOKED";
  schoolVerified?: boolean;
  schoolVerifiedAt?: string;
  studentFeePaid?: boolean;
};
```

`token` 요청값은 공백 불가, 최대 128자입니다. QR 조회는 토큰을 소비하지 않으며 만료 전 재조회할 수 있습니다. 학교 인증 상태와 납부 상태는 정보 표시값이며 QR 발급 조건 자체는 아닙니다.

## 사용자 관리 권한 변경

```http
PATCH /api/qr/users/{userUuid}/role
Authorization: Bearer ADMIN_ACCESS_TOKEN
Content-Type: application/json

{ "managementRole": "STAFF" }
```

성공 응답:

```json
{
  "userUuid": "123e4567-e89b-12d3-a456-426614174099",
  "festivalRoles": ["STAFF"]
}
```

- `managementRole`은 `USER`, `STAFF`, `ADMIN`, `SUPER_ADMIN` 중 하나입니다.
- `ADMIN`은 자신이 아닌 `USER`·`STAFF` 사용자만 `USER ↔ STAFF` 범위에서 변경할 수 있습니다.
- `SUPER_ADMIN`은 모든 관리 권한을 변경할 수 있지만 마지막 SUPER_ADMIN은 강등할 수 없습니다.
- `BOOTH_MANAGER`는 별도 boolean과 담당 부스 관계로 관리하므로 이 API에서 지정하지 않습니다. 부스 지도 관리 화면에서 담당자로 지정하거나 해제합니다.
- 관리 권한을 변경해도 대상 사용자의 기존 부스 관리자 여부와 담당 부스 관계는 유지됩니다.

## 사용자 학생 인증 변경

관리자 페이지의 `사용자 조회` 화면과 동일한 기능을 API로 사용할 수 있습니다.

```http
PATCH /api/qr/users/{userUuid}/school-verification
Authorization: Bearer ADMIN_ACCESS_TOKEN
Content-Type: application/json

{ "verified": true }
```

- `ADMIN`, `SUPER_ADMIN`만 사용할 수 있습니다.
- `verified: true`는 `VERIFIED`와 서버 현재 시각을 기록하고, `false`는 인증 완료 상태를 `REVOKED`로 변경합니다.
- 관리자 임의 인증은 학교 SSO를 호출하거나 `school_subject_hash`를 새로 만들지 않습니다.
- 상태나 인증 시각을 클라이언트가 직접 지정할 수 없습니다.

성공 응답:

```json
{
  "userUuid": "123e4567-e89b-12d3-a456-426614174099",
  "schoolVerificationStatus": "VERIFIED",
  "schoolVerified": true,
  "schoolVerifiedAt": "2026-09-03T03:00:00Z",
  "studentFeePaid": false
}
```

## 오류

- `400 QR_INVALID_OR_EXPIRED`: QR이 잘못됐거나 만료됨. 사용자에게 QR 새로고침 요청
- `401 UNAUTHORIZED`: 운영자 로그인 만료
- `403 QR_SCAN_FORBIDDEN`: 조회 권한 없음
- `400 INVALID_USER_SEARCH_QUERY`: 검색어 길이 오류
- `403 USER_SEARCH_FORBIDDEN`: 사용자 검색 권한 없음
- `400 INVALID_MANAGEMENT_ROLE`: BOOTH_MANAGER 등 잘못된 관리 권한 요청
- `403 USER_ROLE_MANAGE_FORBIDDEN`: ADMIN 미만의 권한 변경 요청
- `403 USER_ROLE_ESCALATION_FORBIDDEN`: ADMIN의 자기 권한·상위 권한 변경 시도
- `403 SCHOOL_VERIFICATION_MANAGE_FORBIDDEN`: ADMIN 미만의 학생 인증 변경 요청
- `404 FESTIVAL_USER_NOT_FOUND`: 축제 연동 사용자가 아님
- `409 LAST_SUPER_ADMIN_REQUIRED`: 마지막 SUPER_ADMIN 강등 시도
