# 스탬프 API

[문서 목차](README.md) · [프런트 연동 시작하기](frontend-getting-started.md)

공통 인증과 오류 처리는 [공통 API 규약](frontend-api-common.md), QR 토큰 발급은 [동적 QR API](frontend-qr-api.md)를 참고합니다.

## 정책과 권한

- 스탬프판은 사용자당 하나이며 회차·초기화 개념이 없습니다. 날짜나 축제 시즌으로 조회 범위를 나누지 않습니다.
- 한 사용자는 한 부스의 현재 스탬프를 최대 하나만 보유합니다.
- 현재 스탬프는 최대 6개입니다. 외부 부스(`EXTERNAL`) 스탬프가 최소 1개 필요하며, 외부 부스 없이 5개를 모았다면 6번째는 외부 부스에서만 받을 수 있습니다. QR·관리자 검색 지급 모두 동일합니다.
- 정책 변경 전 획득 기록은 삭제하지 않습니다. 기존 6개 이상 보유자는 추가 지급이 차단됩니다. 외부 부스가 없다면 관리자가 기존 스탬프를 회수해 5개 이하로 만든 뒤 외부 부스 스탬프를 받아야 합니다. 기존 상품 지급 이력도 유지됩니다.
- 외부 부스 여부는 조회·지급 시 부스의 현재 카테고리로 판단합니다.
- 회수 후 재지급할 수 있으며 모든 `GRANT`, `REVOKE`는 감사 이력에 남습니다.
- `stampEnabled=false`인 부스에서는 지급·조회·회수할 수 없습니다.
- `BOOTH_MANAGER`: 자신에게 배정된 부스에서 QR 방식만 사용합니다.
- `ADMIN`, `SUPER_ADMIN`: 모든 스탬프 부스에서 QR 및 사용자 검색 방식을 사용합니다.
- `STAFF`: 스탬프 관리 권한이 없습니다.
- 단, `STAFF`와 `BOOTH_MANAGER`를 함께 가진 사용자는 담당 부스의 부스 관리자 권한으로 처리할 수 있습니다. 역할 배열 전체를 확인합니다.
- 현재 지급 로직은 학생 인증·학생회비 납부를 필수 조건으로 검사하지 않습니다. 납부자 전용 혜택 여부는 별도 정책이며 자동 적용된다고 가정하지 않습니다.

## 사용자 스탬프판

```http
GET /api/users/me/stamps
Authorization: Bearer ACCESS_TOKEN
```

```ts
type MyStampBoard = {
  participated: boolean;
  stampCount: number;
  stamps: Array<{
    boothId: number;
    boothName: string;
    operator: string;
    category: BoothCategory; // 부스 API의 카테고리 enum, 외부 부스는 EXTERNAL
    grantedAt: string;
  }>;
};
```

성공 `200`:

```json
{
  "participated": true,
  "stampCount": 1,
  "stamps": [
    {
      "boothId": 1,
      "boothName": "체험 부스",
      "category": "EXTERNAL",
      "operator": "운영팀",
      "grantedAt": "2026-08-18T03:00:00Z"
    }
  ]
}
```

`participated`는 과거에 한 번이라도 지급받았으면 모든 현재 스탬프가 회수되어도 `true`입니다. `stampCount`는 현재 보유 개수입니다.

정확히는 현재 스탬프 또는 DB에 남은 `GRANT` 이력이 있을 때 true입니다. 부스의 `stampEnabled`를 끄더라도 내 스탬프판의 기존 획득 기록은 필터링되지 않습니다. 배열은 지급 시각 오름차순입니다.

## QR 조회·지급·회수

| Method | Path | 설명 |
|---|---|---|
| `POST` | `/api/booths/{boothId}/stamps/qr/lookup` | 사용자 및 현재 상태 조회 |
| `POST` | `/api/booths/{boothId}/stamps/qr/grant` | 지급 |
| `POST` | `/api/booths/{boothId}/stamps/qr/revoke` | 회수 |

세 API의 본문은 같습니다.

```json
{ "token": "scanned-temporary-token" }
```

성공 응답:

조회·지급·회수 모두 HTTP 200입니다. QR은 조회만으로 소모되지 않으며 지급·회수 시점에도 유효기간 검사를 받으므로 확인 화면에서 만료되었다면 새 QR을 읽습니다.

```ts
type StampTarget = {
  userUuid: string | null;
  name: string | null;
  studentNo: string | null;
  department: string | null;
  stamped: boolean;
  grantedAt: string | null;
};
```

`BOOTH_MANAGER` 응답에서는 `userUuid=null`, 이름·학번이 마스킹됩니다. `ADMIN` 이상은 원본 범위를 봅니다.

### 권장 QR 화면 흐름

1. 카메라로 token을 읽습니다.
2. `qr/lookup`을 한 번 호출합니다.
3. `stamped=false`면 “지급”, `true`면 “회수” 버튼을 표시합니다.
4. 사용자 확인 후 `grant` 또는 `revoke`를 호출합니다.
5. 성공 응답의 `stamped`, `grantedAt`으로 화면을 즉시 갱신합니다.
6. `409`가 발생하면 상태가 다른 관리자에 의해 바뀐 것이므로 `lookup`을 다시 호출합니다.

동일 QR이 연속 인식되지 않도록 요청 중에는 스캐너를 잠그고, 처리 완료 후 명시적으로 다시 활성화합니다.

## ADMIN 이상 사용자 검색 처리

```http
POST /api/booths/{boothId}/stamps/users/{userUuid}/grant
POST /api/booths/{boothId}/stamps/users/{userUuid}/revoke
Authorization: Bearer ADMIN_ACCESS_TOKEN
```

본문은 없습니다. 성공 응답은 `StampTarget`입니다. 프런트가 임의 UUID를 입력받는 화면을 만들기보다 서버 관리자 화면처럼 연결된 사용자 검색 결과에서 선택하게 하십시오.

## 부스별 현재 현황과 감사 이력

```http
GET /api/booths/{boothId}/stamps/history?page=0&size=30
Authorization: Bearer OPERATOR_ACCESS_TOKEN
```

- `page`: 0부터 시작, 기본 0
- `size`: 기본 30, 최대 100
- `currentStamps`: 현재 보유자 전체
- `history`: 해당 페이지의 감사 이력, `occurredAt` 최신순

```ts
type BoothStampAdmin = {
  boothId: number;
  boothName: string;
  currentStamps: Array<{
    userUuid: string | null;
    name: string;
    studentNo: string | null;
    grantedAt: string;
    grantedBy: string | null;
    method: "QR" | "ADMIN_SEARCH";
  }>;
  history: Array<{
    id: number;
    action: "GRANT" | "REVOKE";
    method: "QR" | "ADMIN_SEARCH";
    targetUserUuid: string | null;
    targetName: string;
    actorUuid: string | null;
    actorName: string;
    occurredAt: string;
  }>;
  historyPage: number;
  historySize: number;
  historyTotalElements: number;
  historyTotalPages: number;
};
```

`BOOTH_MANAGER`에게는 현재 사용자·대상 사용자·처리자 UUID가 `null`이며 개인정보가 마스킹됩니다.

## 주요 오류

| HTTP | code | 화면 처리 |
|---|---|---|
| `400` | `QR_INVALID_OR_EXPIRED` | 사용자에게 QR 새로고침 요청 |
| `400` | `STAMP_DISABLED_BOOTH` | 부스 선택 목록 새로고침 |
| `400` | `STAMP_USER_NOT_LINKED` | 축제 서비스 미연결 사용자 안내 |
| `403` | `STAMP_MANAGE_FORBIDDEN` | 담당 부스/권한 없음 |
| `404` | `BOOTH_NOT_FOUND` | 부스 목록으로 이동 |
| `409` | `STAMP_BOARD_FULL` | 이미 6개 이상 보유하여 추가 지급 불가 |
| `409` | `STAMP_EXTERNAL_REQUIRED` | 6번째 스탬프는 외부 부스에서 받아야 함 |
| `409` | `STAMP_ALREADY_GRANTED` | 이미 지급됨, lookup 재호출 |
| `409` | `STAMP_NOT_GRANTED` | 이미 회수됨, lookup 재호출 |

관리자 HTML 화면은 `/admin/stamps`입니다.

## 관리자 상품 지급 확인

- 관리자 **스탬프 지급 관리 → 상품 지급 관리**(`/admin/stamps/prizes`)에서 사용합니다.
- `ADMIN`, `SUPER_ADMIN`만 조회·지급할 수 있습니다. `STAFF`, `BOOTH_MANAGER`는 직접 요청해도 403입니다.
- 이름·학번·아이디로 직접 검색하여 사용자를 선택하거나, 카메라로 사용자 QR을 읽으면 이름·학번·학과, 현재 스탬프 수, 획득 부스 목록, 상품 지급 여부를 표시합니다.
- 서로 다른 부스의 현재 스탬프가 **외부 부스(`EXTERNAL`) 1개 이상을 포함하여 6개**이고 상품을 받지 않은 경우에만 지급 버튼이 표시됩니다. 기존 스탬프판과 동일하게 획득 후 비활성화된 부스의 스탬프도 포함합니다.
- 지급 버튼을 누르면 현재 스탬프 수와 기존 상품 지급 여부를 다시 검사합니다. QR 지급은 QR 유효기간도 검사하며 만료 시 새 QR을 스캔합니다. 직접 조회 지급은 QR 없이 가능합니다.
- 유효한 상품 지급은 사용자당 1건이며 지급 시각·관리자 UUID·지급 당시 스탬프 수를 기록합니다. 동시에 여러 관리자가 지급해도 한 건만 저장합니다.
- 상품 지급 후 스탬프판을 초기화하지 않습니다. 스탬프를 회수·재지급해도 상품 지급 이력은 유지합니다. 오지급은 ADMIN 이상이 사유(1~500자)를 남겨 철회할 수 있습니다. 철회 후 사용자 스탬프판을 다시 확인하여 재지급할 수 있으며 모든 지급·철회 이력을 보존합니다. 오래된 화면의 철회 요청은 버전 검증으로 차단합니다.
- 서비스 정보 삭제 시 기존 감사 이력 정책과 같이 상품 지급 기록의 사용자·관리자 UUID를 익명화합니다.

아래는 관리자 쿠키·CSRF를 사용하는 **HTML 화면용 요청**입니다. 사용자 Bearer REST API가 아닙니다.

| Method | Path | 입력·동작 |
|---|---|---|
| `GET` | `/admin/stamps/prizes` | QR·직접 조회 화면 |
| `POST` | `/admin/stamps/prizes/search` | form `query`, `page`(기본 0), 사용자 검색(페이지당 20명) |
| `GET` | `/admin/stamps/prizes/users/{userUuid}` | 사용자 스탬프판·상품 상태 |
| `POST` | `/admin/stamps/prizes/users/{userUuid}/grant` | 직접 지급 완료 기록 |
| `GET` | `/admin/stamps/prizes/manage?page=0` | 지급·철회 상태와 최근 지급순 목록, 현재 지급 완료 인원 |
| `GET` | `/admin/stamps/prizes/records/{id}?page=0` | 지급 상세·최신 처리순 이력(페이지당 20건) |
| `POST` | `/admin/stamps/prizes/records/{id}/revoke` | form `version`, `reason`, 오지급 철회 |
| `POST` | `/admin/stamps/prizes/qr/lookup` | form `token`, 스탬프판·상품 지급 상태 확인 |
| `POST` | `/admin/stamps/prizes/qr/grant` | form `token`, 상품 지급 완료 기록 |

업무 오류는 화면에 안내합니다. 서비스 오류 코드는 `STAMP_PRIZE_NOT_READY`(6개 미만 또는 외부 부스 없음), `STAMP_PRIZE_ALREADY_GRANTED`(이미 지급), `STAMP_PRIZE_FORBIDDEN`(권한 부족), `STAMP_PRIZE_CHANGED`(다른 관리자가 변경), `STAMP_PRIZE_ALREADY_REVOKED`(이미 철회), `INVALID_STAMP_PRIZE_REASON`(사유 오류), `STAMP_PRIZE_NOT_FOUND`(기록 없음)입니다.

DB에 `festival_stamp_prizes`와 `festival_stamp_prize_events`를 사용합니다. 기존 지급 테이블에는 `issued`, `version` 컬럼이 추가됩니다. `ddl-auto=update` 환경에서는 자동 생성하며 수동 스키마 환경은 [상품 지급 스키마](stamp-prize-schema.sql)를 적용합니다.
