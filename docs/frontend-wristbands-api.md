# 무대 입장 팔찌 지급·조회

[문서 목차](README.md) · [공통 API 규약](frontend-api-common.md) · [관리자 웹 규약](admin-web-api.md)

## 지급 정책과 권한

- 2026 축제 전체 기간에 학생당 현재 유효한 지급 1건입니다. 날짜가 바뀌어도 초기화하지 않습니다.
- 가입자 UUID로 지급할 때 **학생 인증은 필수**입니다. 미가입자는 ADMIN 이상이 학번과 본인을 직접 확인한 후 별도 학번 지급 경로를 사용합니다. 학생회비 납부 여부는 참고 정보이며 미납부자도 지급할 수 있습니다.
- STAFF·ADMIN·SUPER_ADMIN은 관리자 화면에서 QR·수동 조회와 지급을 수행합니다. BOOTH_MANAGER만 가진 사용자는 사용할 수 없습니다.
- ADMIN·SUPER_ADMIN만 전체 목록·현재 지급 인원·처리 이력을 조회하고 지급을 철회할 수 있습니다. 다른 담당자가 지급한 건도 철회할 수 있습니다.
- 철회는 공백만으로 구성되지 않은 1–500자의 사유가 필수입니다. 철회 후 재지급할 수 있으며 지급·철회·재지급 이력은 모두 남습니다.
- QR·수동 조회 자체는 지급하지 않습니다. 사용자 확인 후 별도의 지급 버튼으로 처리하고, 성공 안내를 확인한 뒤 실물 팔찌를 전달합니다.

## 관리자 화면

메뉴는 **무대 입장 팔찌 지급**(`/admin/wristbands`)입니다. 관리자 전용 쿠키와 POST 폼의 CSRF 토큰을 사용하며, 일반 Bearer REST API와 구분합니다.

**사용자 및 권한 관리**(`/admin/qr`)의 수동 검색·QR 조회 결과에도 STAFF 이상에게 현재 팔찌 지급 여부를 표시합니다. 지급·철회 후 다시 조회하면 갱신된 상태를 확인할 수 있습니다. 학생 미인증이고 연결된 지급 기록이 없으면 재가입 사용자의 상태를 단정하지 않고 인증 후 확인을 안내합니다. 이 표시는 관리자 HTML 전용이며 기존 QR API JSON 필드는 유지합니다.

| 메서드·경로 | 권한 | 입력·결과 |
|---|---|---|
| `GET /admin/wristbands` | STAFF 이상 | QR 카메라·수동 검색 화면 |
| `POST /admin/wristbands/scan` | STAFF 이상 | `token`: 유효한 사용자 QR 토큰, 조회 결과 HTML |
| `POST /admin/wristbands/search` | STAFF 이상 | `query`: 기존 사용자 검색 규칙, `page`: 0부터; 20명씩 조회 |
| `POST /admin/wristbands/issue` | STAFF 이상 | `userUuid`: 조회한 대상 UUID; 지급 후 리다이렉트·성공/오류 안내 |
| `POST /admin/wristbands/manual/search` | ADMIN 이상 | `studentNo`: 마스킹 없는 10자리 학번; 납부 여부·지급 기록 조회 HTML |
| `POST /admin/wristbands/manual/issue` | ADMIN 이상 | `studentNo`, 선택 입력 `name`(200자 이내)·`department`(100자 이내); 지급 후 상세로 리다이렉트 |
| `POST /admin/wristbands/records/{id}/profile` | ADMIN 이상 | `version`, 선택 입력 `name`·`department`; 학번 직접 지급 기록의 수령자 정보 수정 |
| `GET /admin/wristbands/manage` | ADMIN 이상 | `page`: 0부터; 최신 변경 순 20건, 현재 지급 완료 인원 |
| `GET /admin/wristbands/records/{id}` | ADMIN 이상 | `page`: 0부터; 지급·철회 이력 최신순 20건 |
| `POST /admin/wristbands/records/{id}/revoke` | ADMIN 이상 | `version`: 조회한 버전, `reason`: 철회 사유; 상세로 리다이렉트 |

검색은 기존 QR 사용자 검색과 같은 이름·학번·아이디 등의 검색을 사용합니다. STAFF 화면은 이름·학번을 마스킹하고 학과, 학생 인증, 납부, 지급 여부·시각만 보여줍니다. 지급 폼에는 처리 대상 UUID가 포함됩니다. 기존 `/api/qr/search`의 STAFF 응답 필드는 변경하지 않습니다. ADMIN 이상은 지급 기록의 수령자 이름·UUID와 처리자 이름·UUID를 조회할 수 있습니다.

목록은 철회된 기록도 포함합니다. 현재 지급 인원은 철회 상태를 제외합니다. 지급·철회 시 서버에서 상태와 권한을 다시 검증하므로 화면의 버튼 비활성화만으로 권한을 판단하지 않습니다. 오래된 상세 화면에서 이전 지급을 철회하려 하면 버전 불일치로 거부합니다.

### 미가입자 학번 조회·지급·회수

ADMIN·SUPER_ADMIN 화면에 **미가입자 · 학번 직접 조회**가 표시됩니다. 전체 학번으로만 정확히 조회하며 이름·학과로 미가입자를 검색하지 않습니다. 학생회비 명단의 정확한 학번으로 납부 여부를 확인하고, 기존 팔찌 기록도 같은 학번 HMAC으로 찾습니다. 조회 시 가입 여부를 SSO에 질의하지 않으므로 이 조회 자체가 미가입 여부를 보증하지는 않습니다.

학생증 등으로 본인·학번을 확인한 뒤 지급합니다. 이름·학과는 선택 입력이며 이름을 생략하면 `미입력`으로 저장합니다. 지급 후 상세에서 이름·학과를 수정하거나 기존 사유 필수 철회 기능으로 회수 처리할 수 있습니다. 가입자 프로필은 이 폼으로 수정할 수 없습니다. 수정에도 조회한 버전이 필요하고 처리자·시각이 이력에 남습니다. 학번은 기록의 동일인 기준이므로 수정할 수 없습니다.

로그인 계정·가상 UUID·학생 인증은 생성하지 않습니다. 학번 직접 지급 기록은 수령자 UUID가 null이고 학번·선택 이름·학과를 ADMIN 이상에게 표시합니다. 미가입자에게 지급한 뒤 가입하고 학생 인증을 완료하면 같은 학번 기준으로 지급 사실을 확인하며 중복 지급이 차단됩니다. 지급/철회/재지급은 가입자 경로와 동일한 DB 잠금과 기록을 공유합니다. 회원 가입 전 직접 입력한 정보는 학교 인증 정보가 아닙니다.

## 사용자 본인의 지급 여부

```http
GET /api/users/me/wristband
Authorization: Bearer ACCESS_TOKEN
```

성공은 `200 OK`, `Cache-Control: no-store`입니다. 기존 Refresh 쿠키·`X-Access-Token` 갱신 규칙을 유지합니다. UUID를 요청으로 받지 않고 로그인한 사용자의 UUID만 사용합니다.

```ts
type MyWristband = {
  issued: boolean;
  issuedAt: string | null; // ISO-8601 Instant, 현재 유효한 지급 시각
  schoolVerified: boolean;
};
```

```json
{
  "issued": true,
  "issuedAt": "2026-09-16T09:30:00Z",
  "schoolVerified": true
}
```

```json
{
  "issued": false,
  "issuedAt": null,
  "schoolVerified": true
}
```

`issued=true`이면 **팔찌 지급 완료**로 표시합니다. 미지급 또는 철회는 false입니다. 지급자 정보·철회 사유·학생 식별 해시는 사용자 API에 노출하지 않습니다. 학생 인증이 나중에 회수되어도 이미 지급한 사실은 true로 유지하며, 관리자의 철회로만 미지급 상태가 됩니다.

새 UUID로 가입한 사용자는 **학생 인증 후** 과거 지급 기록과 연결할 수 있습니다. 인증 전 `issued=false, schoolVerified=false`라면 “학생 인증 후 지급 여부를 확인해 주세요”로 표시합니다. 이 응답을 과거 수령 이력이 없다는 보장으로 사용하지 않습니다. 일반 웹에서는 화면 진입·다시 활성화 시 조회하여 최신 상태를 반영합니다.

## 중복 지급·탈퇴·동시 처리

- 가입자 지급은 학생 인증 시 저장한 학번 HMAC-SHA256을 기준으로 사용합니다. 학번 직접 지급은 ADMIN 이상이 본인 확인한 전체 학번의 동일 HMAC을 사용합니다. 계정 UUID가 바뀌거나 미가입자였다가 가입해도 같은 학번이면 중복 지급을 차단합니다.
- 관리자 학생 인증으로 확보한 식별 해시도 사용합니다. 인증 상태만 있고 유효한 해시가 없으면 지급을 거부하고 학생 인증 재확인을 안내합니다.
- 학생 인증 변경·축제 이용 정보 삭제와 동일한 DB 잠금(`student_fee_lock`)을 사용해 지급·철회를 직렬화합니다. 식별 해시와 현재 수령자 UUID에는 각각 UNIQUE 제약도 둡니다. 동일 학생의 동시 요청은 1건만 지급됩니다.
- 축제 서비스 이용 정보 삭제 시 수령자·처리자 UUID를 익명 UUID로 바꾸고 저장된 이름도 `알 수 없음`으로 바꿉니다. **중복 방지를 위한 HMAC 식별값과 지급 상태·시각·처리 이력은 보존**합니다. 재인증 시 유효한 지급 사실을 다시 확인할 수 있습니다.
- SSO에서 직접 수행한 탈퇴는 이 서비스의 저장 기록을 삭제하지 않습니다. 홈페이지의 `DELETE /api/users/me`로 탈퇴하면 축제 이용 정보 삭제와 동일하게 이름·UUID를 익명화하고 중복 지급 방지 기록은 보존합니다. 관리자 화면의 지급 당시 이름은 저장된 값이며 SSO에서 매번 다시 조회하는 값이 아닙니다.
- 철회 사유는 자유 입력 이력으로 보존되므로 개인정보를 적지 않습니다. 게시된 [개인정보처리방침 페이지](terms-pages.md)는 지급·이의 확인 목적 달성 시까지, 늦어도 축제 서비스 종료일까지 보유(감사기록 별도)하도록 안내합니다. 자동 삭제 기한은 아직 구현되어 있지 않으므로 문서 게시만으로 자동 파기가 수행된다고 가정하지 않습니다.

## 주요 오류

관리자 폼의 업무 오류는 화면/리다이렉트 안내로 표시합니다. 아래 상태·코드는 서비스 판정 기준이며 모든 폼 실패가 해당 HTTP JSON으로 내려오는 것은 아닙니다. 권한 거부는 403이며 CSRF 위반은 별도 보안 필터에서 처리합니다.

| 상태·코드 | 의미 |
|---|---|
| `403 WRISTBAND_FORBIDDEN` | 조회·지급 권한 없음 |
| `403 WRISTBAND_MANAGE_FORBIDDEN` | 관리·철회 권한 없음 |
| `409 WRISTBAND_SCHOOL_VERIFICATION_REQUIRED` | 학생 미인증 |
| `409 WRISTBAND_IDENTITY_REQUIRED` | 재가입 중복 판정을 위한 인증 식별값 없음 |
| `409 WRISTBAND_ALREADY_ISSUED` | 동일 계정 또는 같은 학생에게 이미 지급됨 |
| `409 WRISTBAND_STATE_CHANGED` | 이미 철회되었거나 오래된 화면의 버전 |
| `400 WRISTBAND_REASON_REQUIRED` | 철회 사유 누락·길이 초과 |
| `400 INVALID_WRISTBAND_STUDENT_NO` | 학번 직접 조회·지급에 10자리 전체 학번이 필요함 |
| `400 INVALID_WRISTBAND_PROFILE` | 선택 이름·학과 길이 초과 |
| `409 WRISTBAND_MANUAL_PROFILE_REQUIRED` | 가입자 지급 기록에는 직접 정보 수정 불가 |
| `404 WRISTBAND_NOT_FOUND` | 지급 기록 없음 |

QR 만료·사용자 조회·인증 오류는 기존 [QR API](frontend-qr-api.md)와 공통 오류 규약을 따릅니다. SSO 사용자 확인에 실패하면 지급하지 않습니다.

## DB와 배포

신규 테이블은 `festival_wristbands`, `festival_wristband_events`입니다. `ddl-auto=update` 환경은 자동 생성하며 수동 DDL 환경은 [팔찌 스키마](wristband-schema.sql)를 먼저 적용합니다. 기존 학생회비 잠금 테이블과 초기 행도 필요합니다. DB 시간을 저장하는 JDBC 시간대 설정은 기존 서비스 설정을 따르며 API는 UTC Instant, 관리자 화면은 한국 시간으로 표시합니다.

기존 DB에는 배포 전에 [학번 직접 지급 마이그레이션](wristband-manual-migration.sql)을 적용합니다. 두 테이블의 수령자 UUID를 nullable로 변경하고, 팔찌의 학번·학과 컬럼 및 `UPDATE_PROFILE` 이력 유형을 추가합니다. 기존 컬럼의 NOT NULL 완화를 `ddl-auto=update`에만 의존하지 않습니다.

`SYU_SSO_SUBJECT_HASH_SECRET`(미설정 시 학교 SSO client secret)을 바꾸면 이전 지급과 새 인증을 연결할 수 없으므로 기존 학교 인증·납부·팔찌 해시를 함께 이전하는 절차 없이 키를 변경하지 않습니다. 팔찌 테이블을 비우거나 새 시즌에 초기화하는 기능은 없습니다.

검증은 H2 기반 통합 테스트로 권한·CSRF·HTML 렌더링·본인 API·동시 지급·철회/재지급·탈퇴 후 중복 방지를 확인합니다. 운영 MySQL 및 실물 카메라 장비에서의 확인과는 별개입니다.
