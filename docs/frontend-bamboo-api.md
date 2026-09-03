# 대나무숲(익명 채팅) API 및 운영 가이드

대나무숲은 Festa 계정으로 로그인한 사용자만 참여할 수 있는 익명 채팅입니다. 사용자에게는 고정 익명
닉네임만 공개하고, 실제 사용자 UUID와 개인정보는 일반 API 응답에 포함하지 않습니다.

공통 인증, Refresh Cookie, `X-Access-Token` 처리와 공통 오류 형식은
[공통 API 규약](frontend-api-common.md)을 따릅니다.

## 1. 주요 정책

- 읽기·작성·닉네임 설정·신고 모두 Bearer 인증이 필요합니다.
- 사용자 UUID는 소유권, 중복 신고, 작성 제한을 판정하기 위해 DB에는 저장됩니다.
- 사용자는 닉네임을 한 번만 확정할 수 있으며 직접 변경할 수 없습니다.
- 닉네임은 사용자 간 중복될 수 없습니다.
- 관리자는 부적절한 닉네임을 강제로 변경할 수 있습니다.
- 메시지는 물리 삭제하지 않습니다. `HIDDEN` 또는 `DELETED` 상태로 변경하고 원문과 처리 이력을 보존합니다.
- 신고가 누적되어도 메시지를 자동으로 가리지 않고 관리자에게 알림만 보냅니다.
- 일반 사용자는 작성자 신원을 조회할 수 없습니다.
- `SUPER_ADMIN`만 작성자 신원을 조회할 수 있으며 조회 사실은 감사 로그에 기록됩니다.

## 2. 권한

| 기능 | USER | STAFF | ADMIN | SUPER_ADMIN |
|---|---:|---:|---:|---:|
| 대나무숲 조회·작성·신고 | 가능 | 가능 | 가능 | 가능 |
| 신고 및 최근 메시지 관리 | 불가 | 가능 | 가능 | 가능 |
| 메시지 숨김·삭제·복구 | 불가 | 가능 | 가능 | 가능 |
| 작성 차단·닉네임 강제 변경 | 불가 | 가능 | 가능 | 가능 |
| 운영 설정·킬스위치 변경 | 불가 | 불가 | 가능 | 가능 |
| 실제 작성자 신원 조회 | 불가 | 불가 | 불가 | 가능 |

관리자 HTML 화면은 `/admin/bamboo`입니다.

## 3. 타입 정의

```ts
type BambooMessageStatus = "VISIBLE" | "HIDDEN" | "DELETED";

type BambooReportReason =
  | "ABUSE"
  | "SPAM"
  | "SEXUAL"
  | "PERSONAL_INFO"
  | "IMPERSONATION"
  | "OTHER";

type BambooRoom = {
  enabled: boolean;
  readOnly: boolean;
  closesAt: string | null;
  nickname: string | null;
  cursor: number;
};

type BambooMessage = {
  id: number;
  seq: number;
  anonName: string;
  content: string | null;
  status: BambooMessageStatus;
  createdAt: string;
  mine: boolean;
};

type BambooStream = {
  cursor: number;
  messages: BambooMessage[];
};

type BambooPage<T> = {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};
```

`id`와 `seq`의 용도는 다릅니다.

- `id`: 메시지의 불변 식별자이며 React key, 신고 대상, 과거 페이지 커서로 사용합니다.
- `seq`: 작성·상태 변경·관리자 닉네임 변경 순서를 나타내는 실시간 변경 커서입니다.
- 프런트는 `id`가 같은 메시지를 새로 추가하지 말고 최신 `seq`의 내용으로 교체해야 합니다.

## 4. 권장 진입 흐름

```text
1. GET /api/bamboo
2. enabled=false면 종료 화면 표시
3. nickname=null이면 후보 조회 또는 직접 입력 후 닉네임 확정
4. GET /api/bamboo/messages?before=9223372036854775807&size=50
5. 응답 cursor를 liveCursor로 저장
6. GET /api/bamboo/messages?after={liveCursor}&size=200 반복 호출
7. 응답을 id 기준으로 추가·교체·제거
```

실시간 전송은 WebSocket이나 SSE가 아니라 커서 기반 폴링입니다. 화면이 보이는 동안 2~5초 간격으로
조회하고, 브라우저 탭이 숨겨졌을 때는 간격을 늘리거나 중단하는 방식을 권장합니다.

## 5. 방 상태 조회

```http
GET /api/bamboo
Authorization: Bearer ACCESS_TOKEN
```

성공 `200`:

```json
{
  "enabled": true,
  "readOnly": false,
  "closesAt": "2026-10-06T12:00:00Z",
  "nickname": "졸린사자42",
  "cursor": 103
}
```

- `enabled=false`: 대나무숲이 닫힌 상태이므로 메시지 API 호출을 중단합니다.
- `readOnly=true`: 조회는 가능하지만 작성은 불가능합니다.
- `closesAt`: UTC ISO-8601 `Instant`이며 화면에서는 KST로 변환합니다.
- 종료 시각이 지나면 서버가 자동으로 `readOnly=true`로 계산합니다. `enabled` 자체가 자동으로 바뀌지는 않습니다.
- `nickname=null`: 아직 닉네임을 확정하지 않은 사용자입니다.

## 6. 닉네임

### 6.1 사용 가능한 후보 조회

```http
GET /api/bamboo/nickname/suggest
Authorization: Bearer ACCESS_TOKEN
```

성공 `200`:

```json
{ "nickname": "졸린사자42" }
```

후보는 조회하는 순간 선점되지 않습니다. 다른 사용자가 먼저 확정하면 확정 요청에서 `409`가 발생할
수 있으므로 새 후보를 다시 조회합니다.

### 6.2 닉네임 확정

```http
POST /api/bamboo/nickname
Authorization: Bearer ACCESS_TOKEN
Content-Type: application/json

{ "nickname": "졸린사자42" }
```

성공 `201`:

```json
{ "nickname": "졸린사자42" }
```

닉네임 규칙:

- Unicode 코드포인트 기준 2~12자
- 한글·영문·숫자·`_`·`-`만 허용
- 공백과 이모지 사용 불가
- 대소문자, 전각 문자, 제로폭 문자와 일부 유사 문자를 정규화한 뒤 중복 판정
- `관리자`, `운영진`, `총학`, `staff`, `admin` 등 운영 주체를 사칭하는 표현 사용 불가
- 사용자가 확정한 뒤에는 다시 변경할 수 없음

## 7. 메시지 조회

과거 메시지와 실시간 변경은 같은 응답 타입을 사용하지만 쿼리 파라미터가 다릅니다. `before`와
`after`를 동시에 보내면 `400 INVALID_PARAMETER`입니다.

### 7.1 최근·과거 메시지

```http
GET /api/bamboo/messages?before=9223372036854775807&size=50
Authorization: Bearer ACCESS_TOKEN
```

- `before`: 이 값보다 작은 메시지 `id`를 조회합니다.
- `size`: 기본 50, 최소 1, 최대 100으로 서버에서 보정됩니다.
- `VISIBLE` 메시지만 반환합니다.
- 응답 배열은 화면에 바로 붙일 수 있도록 오래된 메시지부터 정렬됩니다.
- 더 오래된 페이지는 현재 보유 메시지 중 가장 작은 `id`를 다음 `before`로 사용합니다.

```text
GET /api/bamboo/messages?before={현재 목록의 최소 id}&size=50
```

과거 페이지를 추가로 불러올 때 반환되는 `cursor`로 현재 실시간 `liveCursor`를 덮어쓰지 마십시오.
과거 목록 조회의 `cursor`는 최초 진입 시 실시간 폴링 시작점을 함께 얻기 위한 값입니다.

### 7.2 실시간 변경 조회

```http
GET /api/bamboo/messages?after=103&size=200
Authorization: Bearer ACCESS_TOKEN
```

- `after`: 마지막으로 정상 처리한 응답의 `cursor`입니다.
- `size`: 기본·최대 200이며 최소 1로 보정됩니다.
- 신규 메시지뿐 아니라 숨김·삭제·복구·닉네임 강제 변경도 반환됩니다.
- 응답은 `seq` 오름차순입니다.
- 요청이 성공한 경우에만 응답 `cursor`를 다음 `liveCursor`로 저장합니다.
- 항목이 없더라도 서버가 반환한 `cursor`를 그대로 사용합니다.

성공 `200`:

```json
{
  "cursor": 105,
  "messages": [
    {
      "id": 22,
      "seq": 105,
      "anonName": "졸린사자42",
      "content": "무대가 멋있어요",
      "status": "VISIBLE",
      "createdAt": "2026-10-06T10:00:00Z",
      "mine": false
    }
  ]
}
```

| status | content | 권장 처리 |
|---|---|---|
| `VISIBLE` | 문자열 | `id` 기준 추가 또는 기존 항목 교체 |
| `HIDDEN` | `null` | 해당 `id`를 화면에서 제거 |
| `DELETED` | `null` | 해당 `id`를 화면에서 제거 |

복구된 메시지는 다시 `VISIBLE`과 원문으로 전달됩니다. 관리자 닉네임 변경 시 해당 작성자의 기존
메시지들이 새 `seq`와 변경된 `anonName`으로 전달됩니다.

## 8. 메시지 작성

```http
POST /api/bamboo/messages
Authorization: Bearer ACCESS_TOKEN
Content-Type: application/json

{ "content": "무대가 멋있어요" }
```

성공 `201`이며 생성된 `BambooMessage`를 반환합니다.

- 닉네임을 먼저 확정해야 합니다.
- Unicode 코드포인트 기준 최대 200자, 줄바꿈 최대 5개입니다.
- 연속 공백과 과도한 줄바꿈은 서버에서 정리합니다.
- 설정된 금칙어가 포함되면 거부합니다.
- 기본 최소 작성 간격은 5초, 분당 10건, 시간당 200건입니다.
- 직전에 보낸 메시지와 같으면 중복 작성으로 거부합니다.

프런트는 요청 중 작성 버튼을 비활성화하고 `201` 응답을 받은 뒤 입력창을 비웁니다. 폴링으로 같은
메시지가 다시 도착할 수 있으므로 반드시 `id` 기준으로 중복을 제거합니다.

## 9. 신고

```http
POST /api/bamboo/messages/{id}/report
Authorization: Bearer ACCESS_TOKEN
Content-Type: application/json

{ "reason": "SPAM" }
```

성공은 `204 No Content`입니다.

| reason | 의미 |
|---|---|
| `ABUSE` | 욕설·비방 |
| `SPAM` | 도배·광고 |
| `SEXUAL` | 음란·성적 콘텐츠 |
| `PERSONAL_INFO` | 개인정보 노출 |
| `IMPERSONATION` | 사칭 |
| `OTHER` | 기타 |

- 본인 메시지는 신고할 수 없습니다.
- 같은 사용자는 같은 메시지를 한 번만 신고할 수 있습니다.
- 숨김·삭제된 메시지는 일반 사용자가 신고할 수 없습니다.
- 기본 신고 제한은 사용자당 분당 10건입니다.
- 누적 신고 수가 기준에 도달하면 관리자 메일을 발송하지만 메시지를 자동 차단하지는 않습니다.

## 10. 관리자 REST API

### 10.1 신고 메시지 목록

```http
GET /api/admin/bamboo/reports?page=0&size=30
Authorization: Bearer STAFF_ACCESS_TOKEN
```

- `STAFF` 이상
- `page`: 0부터 시작
- `size`: 최소 1, 최대 100
- 신고 수 내림차순, 같은 신고 수에서는 최신 메시지 우선
- 숨김·삭제 메시지도 원문을 포함하여 반환

```ts
type BambooAdminMessage = {
  id: number;
  seq: number;
  anonName: string;
  content: string;
  status: BambooMessageStatus;
  reportCount: number;
  reasons: Partial<Record<BambooReportReason, number>>;
  createdAt: string;
};

type BambooAdminPage = BambooPage<BambooAdminMessage>;
```

### 10.2 메시지 상태 일괄 변경

```http
PATCH /api/admin/bamboo/messages
Authorization: Bearer STAFF_ACCESS_TOKEN
Content-Type: application/json

{ "ids": [21, 22], "status": "HIDDEN" }
```

성공 `200`:

```json
{ "changed": 2 }
```

- `ids`: 1~100개, 양수만 허용
- `VISIBLE`: 다시 표시
- `HIDDEN`: 운영상 임시 가림
- `DELETED`: 삭제 처리하지만 DB 원문은 보존
- 이미 같은 상태인 메시지는 `changed`에 포함하지 않음

### 10.3 작성자 작성 차단·해제

```http
POST /api/admin/bamboo/messages/{id}/mute-author
Authorization: Bearer STAFF_ACCESS_TOKEN
Content-Type: application/json

{ "minutes": 30 }
```

성공 `200`:

```json
{ "mutedUntil": "2026-10-06T10:30:00Z" }
```

- `minutes`: 0~525600
- `0`: 차단 해제이며 `mutedUntil=null`
- 지정한 메시지의 작성자를 차단하지만 STAFF 화면에는 실제 신원을 공개하지 않음
- 차단 사용자는 읽기와 신고는 가능하고 새 메시지 작성만 불가능

### 10.4 작성자 닉네임 강제 변경

```http
PATCH /api/admin/bamboo/messages/{id}/author-nickname
Authorization: Bearer STAFF_ACCESS_TOKEN
Content-Type: application/json

{ "nickname": "차분한코알라11" }
```

성공 `200`:

```json
{ "nickname": "차분한코알라11" }
```

해당 작성자의 닉네임과 과거 메시지 표시 이름이 모두 변경됩니다. 변경된 메시지는 실시간 변경 조회에도
새 이벤트로 전달됩니다.

### 10.5 작성자 신원 조회

```http
GET /api/admin/bamboo/messages/{id}/author
Authorization: Bearer SUPER_ADMIN_ACCESS_TOKEN
```

`SUPER_ADMIN` 전용이며 조회 성공·실패 모두 감사 로그에 기록됩니다.

```json
{
  "userUuid": "123e4567-e89b-12d3-a456-426614174000",
  "nickname": "졸린사자42",
  "loginId": "student01",
  "email": "student@example.com",
  "name": "홍길동",
  "phone": "01012345678",
  "studentNo": "2026123456",
  "department": "컴퓨터공학부",
  "grade": 2,
  "enrollment": "재학"
}
```

응답 개인정보를 브라우저 저장소, analytics, 콘솔 로그에 저장하지 않습니다.

### 10.6 운영 설정 조회·변경

```http
GET /api/admin/bamboo/settings
Authorization: Bearer STAFF_ACCESS_TOKEN
```

조회는 `STAFF` 이상입니다.

```json
{
  "enabled": true,
  "readOnly": false,
  "closesAt": "2026-10-06T12:00:00Z",
  "updatedAt": "2026-10-06T08:00:00Z"
}
```

```http
PATCH /api/admin/bamboo/settings
Authorization: Bearer ADMIN_ACCESS_TOKEN
Content-Type: application/json

{
  "enabled": true,
  "readOnly": false,
  "closesAt": "2026-10-06T12:00:00Z",
  "clearClosesAt": false
}
```

변경은 `ADMIN` 이상입니다. `null`인 필드는 기존 값을 유지합니다.

- `enabled=false`: 사용자 메시지 조회·작성·신고를 모두 닫는 킬스위치
- `readOnly=true`: 조회만 허용하고 작성 차단
- `closesAt`: 해당 시각 이후 자동 읽기 전용
- `clearClosesAt=true`: 저장된 종료 시각 제거

설정 API의 `readOnly`는 관리자가 저장한 원본 값입니다. 사용자용 `GET /api/bamboo`의 `readOnly`는
저장값과 종료 시각을 함께 계산한 결과입니다.

## 11. 주요 오류

| HTTP | code | 권장 처리 |
|---|---|---|
| `400` | `INVALID_PARAMETER` | `before`와 `after` 동시 사용 등 파라미터 확인 |
| `400` | `BAMBOO_NICKNAME_INVALID` | 닉네임 길이·문자 규칙 안내 |
| `400` | `BAMBOO_NICKNAME_BLOCKED` | 다른 닉네임 입력 안내 |
| `400` | `BAMBOO_CONTENT_REQUIRED` | 빈 메시지 안내 |
| `400` | `BAMBOO_CONTENT_TOO_LONG` | 최대 200자 안내 |
| `400` | `BAMBOO_TOO_MANY_LINE_BREAKS` | 줄바꿈 최대 5개 안내 |
| `400` | `BAMBOO_CONTENT_BLOCKED` | 사용할 수 없는 표현 안내 |
| `400` | `BAMBOO_DUPLICATE_MESSAGE` | 직전 메시지와 같은 내용 안내 |
| `400` | `BAMBOO_SELF_REPORT_NOT_ALLOWED` | 본인 메시지 신고 UI 비활성화 |
| `400` | `BAMBOO_INVALID_MUTE_DURATION` | 관리자 입력값 확인 |
| `401` | `UNAUTHORIZED` | 토큰 갱신 또는 로그인 화면 이동 |
| `403` | `BAMBOO_CLOSED` | 운영 종료 화면 표시 및 폴링 중단 |
| `403` | `BAMBOO_READ_ONLY` | 작성 UI 비활성화 |
| `403` | `BAMBOO_MUTED` | 서버 메시지로 남은 차단 시간 안내 |
| `403` | `BAMBOO_MANAGE_FORBIDDEN` | 관리자 권한 없음 |
| `404` | `BAMBOO_MESSAGE_NOT_FOUND` | 목록 동기화 후 대상 제거 |
| `404` | `BAMBOO_NICKNAME_NOT_FOUND` | 관리자 목록 새로고침 |
| `409` | `BAMBOO_NICKNAME_REQUIRED` | 닉네임 설정 화면 표시 |
| `409` | `BAMBOO_NICKNAME_ALREADY_SET` | 방 상태 재조회 |
| `409` | `BAMBOO_NICKNAME_TAKEN` | 다른 닉네임 선택 |
| `409` | `BAMBOO_ALREADY_REPORTED` | 신고 완료 상태로 표시 |
| `413` | `BAMBOO_REQUEST_TOO_LARGE` | 비정상적으로 큰 요청 차단 |
| `429` | `BAMBOO_TOO_FAST` | 작성 버튼을 잠시 비활성화 |
| `429` | `BAMBOO_RATE_LIMIT` | 잠시 후 재시도 안내 |
| `429` | `BAMBOO_BUSY` | `Retry-After` 헤더 이후 재시도 |
| `503` | `BAMBOO_NICKNAME_UNAVAILABLE` | 잠시 후 후보 재요청 또는 직접 입력 |

`Retry-After`, 갱신된 `X-Access-Token`, `X-Request-ID`는 CORS 노출 헤더에 포함되어 있습니다.

## 12. 프런트 보안·개인정보 처리

- 닉네임과 메시지 본문은 신뢰하지 않는 사용자 입력입니다.
- React/Vue의 일반 텍스트 바인딩 또는 DOM `textContent`로 출력합니다.
- 사용자 입력을 `innerHTML`, `dangerouslySetInnerHTML`, `v-html`로 렌더링하지 않습니다.
- Access Token, 사용자 UUID, 메시지 본문을 URL, analytics, 오류 수집 도구와 콘솔 로그에 기록하지 않습니다.
- SUPER_ADMIN 작성자 조회 결과를 `localStorage`나 `sessionStorage`에 저장하지 않습니다.
- 폴링 요청이 겹치지 않도록 이전 요청이 끝난 후 다음 요청을 실행합니다.
- 네트워크 오류나 `429` 발생 시 즉시 반복 호출하지 말고 지수 백오프를 적용합니다.

## 13. 서버 보호 정책

- 대나무숲 사용자 API가 동시에 점유할 수 있는 요청 수를 제한합니다.
- 동시 요청 한도 초과 시 다른 부스·스탬프·QR API의 스레드를 남기고 대나무숲 요청만 `429`로 거부합니다.
- `Content-Length`가 16KiB를 초과하는 요청은 컨트롤러 진입 전에 `413`으로 거부합니다.
- Access Token 원문은 인증 캐시에 저장하지 않고 SHA-256 해시만 키로 사용합니다.
- 관리 API는 인증 캐시를 사용하지 않고 매 요청 SSO 권한을 확인합니다.
- 일반 사용자 인증 결과는 SSO 부하 보호를 위해 기본 60초 캐시합니다.
- 신고 알림 메일은 별도 스레드와 제한된 큐에서 발송하므로 SMTP 장애가 신고 저장을 롤백하지 않습니다.

## 14. 환경변수

```dotenv
BAMBOO_MAX_CONCURRENT_REQUESTS=100
BAMBOO_WRITE_INTERVAL=5s
BAMBOO_WRITES_PER_MINUTE=10
BAMBOO_WRITES_PER_HOUR=200
BAMBOO_REPORTS_PER_MINUTE=10
BAMBOO_MAX_TRACKED_USERS=100000
BAMBOO_IDENTITY_TTL=60s
BAMBOO_IDENTITY_MAX_ENTRIES=20000

BAMBOO_ALERT_ENABLED=true
BAMBOO_ALERT_THRESHOLD=5
BAMBOO_ALERT_TO=admin@syu-likelion.org
BAMBOO_ALERT_FROM=no-reply@syu-likelion.org
BAMBOO_ALERT_ADMIN_URL=https://festa.syu-likelion.org/admin/bamboo
BAMBOO_ALERT_MAX_PER_MINUTE=10
BAMBOO_ALERT_PREVIEW_LENGTH=50
```

- `BAMBOO_ALERT_TO`가 비어 있으면 신고는 저장되지만 메일은 발송하지 않습니다.
- `BAMBOO_ALERT_FROM`은 SMTP 계정에서 발송 권한이 있는 주소여야 합니다.
- 본문 금칙어는 `bamboo.blocked-words[n]` Spring 설정으로 추가할 수 있습니다.

## 15. DB와 배포

사용 테이블:

```text
bamboo_messages
bamboo_nicknames
bamboo_reports
bamboo_settings
```

운영 DB에는 애플리케이션 실행 전에 [대나무숲 스키마](bamboo-schema.sql)를 적용합니다. 채팅에서 이모지를
정상 저장하려면 네 테이블 모두 `utf8mb4` 문자셋이어야 합니다.

현재 변경 커서 발급기는 단일 Festa 애플리케이션 인스턴스를 전제로 합니다. 동일 DB에 연결된 서버를
두 대 이상 동시에 실행하기 전에는 DB 시퀀스 또는 Redis 기반 전역 커서 발급기로 교체해야 합니다.

## 16. 배포 전 확인사항

- [ ] `bamboo-schema.sql` 적용
- [ ] 네 테이블의 collation이 `utf8mb4`인지 확인
- [ ] Festa 애플리케이션이 단일 인스턴스로 실행되는지 확인
- [ ] `BAMBOO_ALERT_TO` 실제 수신 주소 설정
- [ ] SMTP 계정에서 `no-reply@syu-likelion.org` 발송 권한 확인
- [ ] Nginx에 `/api/bamboo` 요청 크기 제한 설정
- [ ] 프런트가 `id` 기준으로 메시지를 갱신하는지 확인
- [ ] 최초 과거 조회 cursor와 이후 실시간 cursor를 구분하는지 확인
- [ ] `HIDDEN`, `DELETED` 이벤트를 화면에서 제거하는지 확인
- [ ] `Retry-After`와 토큰 갱신 헤더를 처리하는지 확인
- [ ] 닉네임·본문을 HTML이 아닌 텍스트로 렌더링하는지 확인
- [ ] STAFF·ADMIN·SUPER_ADMIN 권한별 관리자 화면 확인
- [ ] 실제 MySQL에서 동시 작성·숨김·복구·닉네임 변경 테스트
- [ ] 실제 SMTP 신고 알림 발송 테스트
