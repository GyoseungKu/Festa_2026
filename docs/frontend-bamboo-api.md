# 대나무숲(익명 채팅) API 및 운영 가이드

[문서 목차](README.md) · [프런트 연동 시작하기](frontend-getting-started.md)

**이 문서의 순서**

- [1. 주요 정책](#1-주요-정책)
- [2. 권한](#2-권한)
- [3. 타입 정의](#3-타입-정의)
- [4. 권장 진입 흐름](#4-권장-진입-흐름)
- [5. 방 상태 조회](#5-방-상태-조회)
- [6. 닉네임](#6-닉네임)
- [7. 메시지 조회](#7-메시지-조회)
- [8. 메시지 작성](#8-메시지-작성)
- [9. 신고](#9-신고)
- [10. 관리자 REST API](#10-관리자-rest-api)
- [11. 주요 오류](#11-주요-오류)
- [12. 프런트 보안·개인정보 처리](#12-프런트-보안개인정보-처리)
- [13. 서버 보호 정책](#13-서버-보호-정책)
- [14. 환경변수](#14-환경변수)
- [15. DB와 배포](#15-db와-배포)
- [16. 배포 전 확인사항](#16-배포-전-확인사항)

대나무숲은 Festa 계정으로 로그인하고 학생 인증을 완료한 사용자만 참여할 수 있는 익명 채팅입니다. 사용자에게는 고정 익명
닉네임만 공개하고, 실제 사용자 UUID와 개인정보는 일반 API 응답에 포함하지 않습니다.

공통 인증, Refresh Cookie, `X-Access-Token` 처리와 공통 오류 형식은
[공통 API 규약](frontend-api-common.md)을 따릅니다.

## 학생 인증 필수

일반 사용자 API는 조회·참여 모두 로그인과 학생 인증이 필요합니다. `schoolVerificationStatus=VERIFIED`이고 인증 시각이 있어야 하며, 미인증·인증 회수 상태는 아래 오류를 반환합니다. 학생회비 납부 여부는 이용 조건이 아닙니다.

```http
HTTP/1.1 403 Forbidden
Content-Type: application/json

{"code":"SCHOOL_VERIFICATION_REQUIRED","message":"학생 인증 완료 후 이용할 수 있습니다."}
```

프런트는 `/api/users/me`의 `schoolVerified`로 진입 화면을 구성하고, 위 오류를 받으면 [학생 인증 흐름](frontend-school-sso.md)으로 안내합니다. 비로그인 요청은 `401`입니다. 일반 사용자 API에는 관리자 역할도 학생 인증 예외가 없으며, 별도 관리자 운영 API·페이지는 기존 역할 권한을 따릅니다. 경로와 성공 응답 구조는 동일하며, 이전에 허용되던 미인증 요청은 이제 `403`으로 거절됩니다.

SSO 로그인 상태와 DB의 학생 인증 상태를 매 요청 확인합니다. 요청 간 인증 캐시는 사용하지 않습니다.

## 1. 주요 정책

- 방 상태·닉네임 추천/설정·메시지 조회/작성·신고 모두 Bearer 인증과 학생 인증이 필요합니다.
- 사용자 UUID는 소유권, 중복 신고, 작성 제한을 판정하기 위해 DB에는 저장됩니다.
- 사용자는 닉네임을 한 번만 확정할 수 있으며 직접 변경할 수 없습니다.
- 닉네임은 사용자 간 중복될 수 없습니다.
- 관리자는 부적절한 닉네임을 강제로 변경할 수 있습니다.
- 메시지는 물리 삭제하지 않습니다. `BLOCKED` 또는 `DELETED` 상태로 변경하고 원문과 처리 이력을 보존합니다.
- 서로 다른 사용자 신고가 5회 이상이면 사용자 응답은 `HIDDEN`이며 원문을 포함합니다. 관리자가 차단하면 `BLOCKED`이며 원문을 전달하지 않습니다.
- 일반 사용자는 작성자 신원을 조회할 수 없습니다.
- `SUPER_ADMIN`만 작성자 신원을 조회할 수 있으며 조회 사실은 감사 로그에 기록됩니다.

### 사용자 응답의 메시지 상태

상태 문자열은 대문자입니다. 사용자 응답은 다음 계약을 따릅니다.

| 조건 | status | content |
|---|---|---|
| 신고 0–4회, 관리자 조치 없음 | `VISIBLE` | 원문 |
| 신고 5회 이상, 관리자 조치 없음 | `HIDDEN` | 원문 |
| 관리자 차단 | `BLOCKED` | null |
| 관리자 삭제 | `DELETED` | null; 변경 조회에만 포함 |

관리자 API·화면에서는 검토를 위해 모든 상태의 원문과 신고 횟수·사유 집계를 유지합니다. 사용자 작성 차단(mute)은 메시지 상태 변경과 별개입니다.

원문이 제공되는 `HIDDEN`은 접근 차단이 아니라 프런트 표시 정책입니다.

## 2. 권한

| 기능 | USER | STAFF | ADMIN | SUPER_ADMIN |
|---|---:|---:|---:|---:|
| 대나무숲 조회·작성·신고 | 학생 인증 필요 | 학생 인증 필요 | 학생 인증 필요 | 학생 인증 필요 |
| 실시간·신고 채팅 운영 화면 | 불가 | 가능 | 가능 | 가능 |
| 메시지 숨김·삭제·복구 | 불가 | 가능 | 가능 | 가능 |
| 메시지 작성자 차단 (신고 여부 무관) | 불가 | 가능 | 가능 | 가능 |
| 차단 해제 | 불가 | 불가 | 가능 | 가능 |
| 닉네임 강제 변경 | 불가 | 가능 | 가능 | 가능 |
| 운영 설정·킬스위치 변경 | 불가 | 불가 | 가능 | 가능 |
| 실제 작성자 신원 조회 | 불가 | 불가 | 불가 | 가능 |

관리자 HTML 화면은 `/admin/bamboo`입니다. 기본 `실시간 채팅` 탭은 3초마다 변경 커서만 확인하고 변경 시 목록을 다시 불러오며, `신고된 채팅`은 별도 탭으로 분리됩니다. ADMIN 이상은 익명 닉네임 검색 결과에서 참여자를 직접 작성 차단·해제할 수 있습니다. 이 검색에는 닉네임만 정하고 아직 메시지를 쓰지 않은 참여자도 포함되며 실제 사용자 신원은 노출하지 않습니다. 모든 차단·해제에는 사유가 필요하며 처리자·처리 시각·대상·기간과 함께 감사 이력에 저장됩니다. 작성자 실제 신원 조회는 기존처럼 SUPER_ADMIN만 가능합니다.

## 3. 타입 정의

```ts
type BambooMessageStatus = "VISIBLE" | "HIDDEN" | "BLOCKED" | "DELETED";

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
3. nickname=null이면 작성 전에 후보 조회 또는 직접 입력 후 닉네임 확정 (읽기는 가능)
4. GET /api/bamboo/messages?before=9223372036854775807&size=50
5. 응답 cursor를 liveCursor로 저장
6. GET /api/bamboo/messages?after={liveCursor}&size=200 반복 호출
7. 응답을 id 기준으로 추가·교체·제거
```

실시간 전송은 WebSocket이나 SSE가 아니라 커서 기반 폴링입니다. 화면이 보이는 동안 2–5초 간격으로
조회하고, 브라우저 탭이 숨겨졌을 때는 간격을 늘리거나 중단하는 방식을 권장합니다.

작성 간격 5초는 사용자별 도배 제한이며 조회 주기를 뜻하지 않습니다. 실제 제한은 `BAMBOO_WRITE_INTERVAL` 설정과 분당·시간당 상한을 함께 적용합니다. 5초만 기다렸어도 다른 상한에 걸릴 수 있습니다.

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
  "cursor": 103,
  "muted": false,
  "mutedUntil": null
}
```

- `enabled=false`: 대나무숲이 닫힌 상태이므로 메시지 API 호출을 중단합니다.
- `readOnly=true`: 조회는 가능하지만 작성은 불가능합니다.
- `closesAt`: UTC ISO-8601 `Instant`이며 화면에서는 KST로 변환합니다.
- 종료 시각이 지나면 서버가 자동으로 `readOnly=true`로 계산합니다. `enabled` 자체가 자동으로 바뀌지는 않습니다.
- `nickname=null`: 아직 닉네임을 확정하지 않은 사용자입니다.

- `muted`: 현재 사용자의 작성 차단 여부입니다. 방 전체의 `readOnly`와 별개입니다.
- `mutedUntil`: 차단 중이면 해제 시각(UTC ISO-8601, 예: `2026-10-06T10:30:00Z`)입니다. 차단되지 않았거나 만료·해제된 경우 `null`이며 `muted=false`입니다. 닉네임 미설정 사용자도 동일합니다.

차단 중에도 조회는 가능하며, 작성 시에는 `403 BAMBOO_MUTED`가 반환됩니다. 운영 설정·개인 차단 변경이 메시지 커서를 반드시 증가시키지는 않으므로 탭 복귀와 주기적인 방 상태 재조회, 쓰기 오류를 함께 처리합니다.

## 6. 닉네임

### 6.1 사용 가능한 후보 조회

```http
GET /api/bamboo/nickname/suggest
Authorization: Bearer ACCESS_TOKEN
```

성공 `200`:

```json
{ "nickname": "수줍은 고방오리" }
```

후보는 `형용사 + 공백 하나 + 새 이름` 형식이며 숫자를 붙이지 않습니다. 예: `수줍은 고방오리`.
형용사는 `졸린`, `조용한`, `배고픈`, `신난`, `느긋한`, `수줍은`, `씩씩한`, `새침한`, `재빠른`의 9개입니다.
새 이름은 제공 목록의 중복 8개를 제거한 609개이며 총 5,481개 조합입니다. 가장 긴 조합도 공백 포함 14자이므로 15자 제한 안에서 형용사·새 이름을 그대로 사용합니다.
생성 규칙은 `bamboo/BambooNicknamePolicy.java`, 중복 확인과 재시도는 `BambooService.suggestNickname()`에 있습니다. 후보를 찾지 못하면 `503 BAMBOO_NICKNAME_UNAVAILABLE`로 직접 입력을 안내하며 숫자 접미사를 붙이는 대체 생성은 하지 않습니다.

후보는 조회하는 순간 선점되지 않습니다. 다른 사용자가 먼저 확정하면 확정 요청에서 `409`가 발생할
수 있으므로 새 후보를 다시 조회합니다.

### 6.2 닉네임 확정

```http
POST /api/bamboo/nickname
Authorization: Bearer ACCESS_TOKEN
Content-Type: application/json

{ "nickname": "수줍은 고방오리" }
```

성공 `201`:

```json
{ "nickname": "수줍은 고방오리" }
```

닉네임 규칙:

- Unicode 코드포인트 기준 공백 포함 2–15자
- 한글·영문·숫자·`_`·`-`만 허용
- 중간 구분 공백 하나 허용. 연속 공백·여러 구분 공백·탭·줄바꿈·이모지는 사용 불가
- 공백 유무는 중복 판정에서 무시하므로 `수줍은 고방오리`와 `수줍은고방오리`는 같은 닉네임
- 대소문자, 전각 문자, 제로폭 문자와 일부 유사 문자를 정규화한 뒤 중복 판정
- `관리자`, `운영진`, `총학`, `staff`, `admin` 등 운영 주체를 사칭하는 표현 사용 불가
- 사용자가 확정한 뒤에는 다시 변경할 수 없음

직접 입력과 관리자 강제 변경에도 15자 제한을 적용합니다. 기존에 확정한 닉네임은 유지하며 자동으로 재생성하지 않습니다. DB 닉네임·정규화 키·메시지 표시 이름 컬럼은 기존 20자이므로 이번 변경에 별도 컬럼 확장은 필요하지 않습니다.

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
- `VISIBLE`, 신고 누적 `HIDDEN`, 관리자 차단 `BLOCKED`를 반환합니다. `BLOCKED`의 본문은 null이며 `DELETED`는 과거 목록에서 제외됩니다.
- 응답 배열은 화면에 바로 붙일 수 있도록 오래된 메시지부터 정렬됩니다.
- 더 오래된 페이지는 현재 보유 메시지 중 가장 작은 `id`를 다음 `before`로 사용합니다.

```text
GET /api/bamboo/messages?before={현재 목록의 최소 id}&size=50
```

과거 페이지를 추가로 불러올 때 반환되는 `cursor`로 현재 실시간 `liveCursor`를 덮어쓰지 마십시오.
과거 목록 조회의 `cursor`는 최초 진입 시 실시간 폴링 시작점을 함께 얻기 위한 값입니다.

최초 `before` 값 `9223372036854775807`은 JavaScript의 안전한 정수 범위를 넘습니다. 숫자로 변환하지 말고 `new URLSearchParams({ before: "9223372036854775807", size: "50" })`처럼 문자열 그대로 전송합니다. `before`와 `after`를 모두 생략하면 최근 메시지가 아니라 현재 커서와 빈 변경 목록을 받으므로 최초 조회에는 위 `before`가 필요합니다.

작성 성공 응답을 화면에 먼저 넣어도 그 `seq`로 `liveCursor`를 앞당기지 않습니다. 아직 받지 못한 다른 사용자의 메시지를 건너뛸 수 있습니다. `liveCursor`는 최초 과거 응답과 이후 순차 처리한 변경 조회 응답으로만 갱신합니다.

같은 시점의 폴링을 겹쳐 실행하지 않고 완료 후 다음 요청을 예약합니다. 과거 페이지와 실시간 변경을 합칠 때는 메시지별 마지막 `seq`를 비교합니다. 차단·삭제로 원문을 제거한 메시지도 마지막 `seq`를 기억해야 늦게 도착한 과거 응답이 해당 내용을 다시 표시하지 않습니다.

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
| `HIDDEN` | 원문 문자열 | 신고 누적 안내로 가리고 사용자가 원하면 펼치기 |
| `BLOCKED` | `null` | 관리자 차단 안내 표시, 원문 펼치기 금지 |
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
- 관리자 차단·삭제된 메시지는 신고할 수 없습니다. 신고 누적 `HIDDEN`은 추가 신고할 수 있습니다.
- 기본 신고 제한은 사용자당 분당 10건입니다.
- 5번째 신고부터 `HIDDEN`과 원문이 전달되며 변경 커서도 갱신됩니다. 작성자 경고 메일 기준은 별도 설정입니다.

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

{ "ids": [21, 22], "status": "BLOCKED" }
```

성공 `200`:

```json
{ "changed": 2 }
```

- `ids`: 1–100개, 양수만 허용
- `VISIBLE`: 관리자 차단 해제. 신고가 이미 5회 이상이면 사용자 응답은 다시 `HIDDEN`입니다. 신고 횟수는 초기화하지 않습니다.
- `BLOCKED`: 관리자 차단, 사용자에게 원문 미전달
- 기존 관리자 클라이언트의 `HIDDEN` 요청도 `BLOCKED`로 처리합니다. 자동 신고 가림을 지정하는 관리 명령이 아닙니다.
- `DELETED`: 삭제 처리하지만 DB 원문은 보존
- 이미 같은 상태인 메시지는 `changed`에 포함하지 않음

### 10.3 작성자 작성 차단·해제

```http
POST /api/admin/bamboo/messages/{id}/mute-author
Authorization: Bearer ADMIN_ACCESS_TOKEN
Content-Type: application/json

{ "minutes": 30, "reason": "반복적인 도배 메시지 작성" }
```

성공 `200`:

```json
{ "mutedUntil": "2026-10-06T10:30:00Z" }
```

- `minutes`: 0–525600
- `reason`: 필수, 1–200자. 차단과 해제 모두 입력
- `0`: 차단 해제이며 `mutedUntil=null`
- `STAFF`는 신고 여부와 관계없이 글의 작성자 차단(`minutes` 1–525600)만 가능. 해제 요청은 `403 BAMBOO_MANAGE_FORBIDDEN`
- `ADMIN`, `SUPER_ADMIN`은 신고 여부와 관계없이 차단·해제 가능
- 처리자 UUID·이름·역할, 처리 시각, 대상 익명 닉네임, 기간, 사유와 근거 메시지 번호를 감사 이력에 저장
- 지정한 메시지의 작성자를 차단하지만 관리자 화면에는 실제 신원을 공개하지 않음
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

### 10.7 관리자 HTML 화면 전용 기능

`/admin/bamboo`는 REST 클라이언트가 아니라 축제 운영자가 사용하는 서버 렌더링 화면입니다.

| 기능 | STAFF | ADMIN | SUPER_ADMIN |
|---|---:|---:|---:|
| 실시간 채팅·신고된 채팅 조회 | 가능 | 가능 | 가능 |
| 메시지 숨김·삭제·복구 | 가능 | 가능 | 가능 |
| 닉네임 강제 변경 | 가능 | 가능 | 가능 |
| 익명 닉네임 참여자 검색 | 불가 | 가능 | 가능 |
| 메시지 작성자 차단 (신고 여부 무관) | 가능 | 가능 | 가능 |
| 닉네임 직접 차단·차단 해제 | 불가 | 가능 | 가능 |
| 차단·해제 감사 이력 조회 | 불가 | 가능 | 가능 |
| 운영 설정 변경 | 불가 | 가능 | 가능 |
| 메시지 작성자 실제 신원 조회 | 불가 | 불가 | 가능 |
| 실제 신원으로 사용자 검색·차단·해제 | 불가 | 불가 | 가능 |

- 기본 탭은 `실시간 채팅`입니다. 브라우저가 3초마다 `/admin/bamboo/cursor`를 조회하고 메시지 변경 커서가 증가했을 때만 현재 페이지를 다시 불러옵니다.
- `신고된 채팅` 탭은 별도로 제공되며 자동 새로고침을 켠 경우 10초마다 갱신합니다.
- 익명 참여자 검색과 감사 이력은 각각 20개 단위 숫자 페이지를 사용합니다.
- 익명 참여자 검색은 닉네임을 정했지만 아직 메시지를 작성하지 않은 사용자도 포함합니다.
- 차단과 해제 모두 사유 입력이 필수입니다. 브라우저의 `required`만 신뢰하지 않고 서버에서도 권한·사유·기간을 다시 검증합니다.
- STAFF가 차단 폼 URL을 직접 호출해도 처리되지 않습니다.
- 감사 이력에는 처리자 UUID·이름·역할, 대상 UUID·당시 닉네임, `MUTE`/`UNMUTE`, 차단 기간, 사유, 근거 메시지 번호와 처리 시각이 저장됩니다.
- 익명 참여자 직접 차단과 감사 이력 조회는 현재 이 HTML 화면 전용이며 공개된 REST API가 아닙니다.

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
| `400` | `BAMBOO_MUTE_REASON_INVALID` | 차단·해제 사유를 1–200자로 입력 |
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
- 일반 사용자·관리 API 모두 매 요청 SSO 인증을 확인합니다. 폐기·차단된 토큰을 요청 간 캐시로 허용하지 않습니다.
- 정상 Refresh Token에 의한 토큰 갱신과 응답 헤더·쿠키 전달은 유지됩니다.
- 요청 간 인증 캐시 제거로 SSO 호출량이 증가합니다. 폴링 중복 방지·백오프와 동시 요청 제한을 유지하고 배포 전 SSO 용량을 확인합니다. 기존 BAMBOO_IDENTITY_TTL/MAX_ENTRIES 설정은 제거되었으며 더 이상 사용하지 않습니다.
- 신고 알림 메일은 별도 스레드와 제한된 큐에서 발송하므로 SMTP 장애가 신고 저장을 롤백하지 않습니다.

## 14. 환경변수

```dotenv
BAMBOO_MAX_CONCURRENT_REQUESTS=100
BAMBOO_WRITE_INTERVAL=5s
BAMBOO_WRITES_PER_MINUTE=10
BAMBOO_WRITES_PER_HOUR=200
BAMBOO_REPORTS_PER_MINUTE=10
BAMBOO_MAX_TRACKED_USERS=100000

BAMBOO_ALERT_ENABLED=true
BAMBOO_ALERT_THRESHOLD=5
BAMBOO_ALERT_FROM=no-reply@syu-likelion.org
BAMBOO_ALERT_SERVICE_URL=https://festa.syu-likelion.org
BAMBOO_ALERT_MAX_PER_MINUTE=10
BAMBOO_ALERT_PREVIEW_LENGTH=50
```

- 수신자는 신고된 메시지 작성자 UUID로 SSO 내부 프로필에서 조회한 이메일입니다. 이메일이 없거나 SSO 조회가 실패하면 발송하지 않으며 관리자에게 대체 발송하지 않습니다. 기존 `BAMBOO_ALERT_TO`와 `BAMBOO_ALERT_ADMIN_URL`은 사용하지 않습니다.
- `BAMBOO_ALERT_FROM`은 SMTP 계정에서 발송 권한이 있는 주소여야 합니다.
- 신고 경고 메일 제목은 `[2026 천보축전] 오픈채팅 이용 경고`입니다. 본문에는 작성 메시지의 일부, 누적 신고 수, 이용 주의·운영정책 안내, 홈페이지 링크와 문의 이메일을 표시합니다. 신고자 정보나 관리자 페이지 링크는 포함하지 않습니다. 신고만으로 위반이 확정됐다고 안내하지 않습니다.
- 메일은 Welcome과 같은 배너(`email-banner.png`)·흰색 본문 카드·홈페이지 버튼·운영정보 Footer 형식입니다. `templates/mail/bamboo-warning.html`에서 경고 내용을 렌더링하며 닉네임과 본문은 HTML 이스케이프합니다. 배너는 CID 첨부, 작성 시각은 KST로 표시하고 일반 텍스트 대체 본문도 함께 제공합니다.
- 메시지별 발송 중복 방지와 분당 제한은 서버 메모리 기준입니다. 재시작·다중 인스턴스에서는 중복 발송될 수 있습니다. 발송 실패·제한 시 별도 자동 재시도 작업은 없고, 이후 신고 요청에서 재시도할 수 있습니다. SSO·SMTP 장애는 신고 저장 결과에 영향을 주지 않습니다.
- 본문 금칙어는 `bamboo.blocked-words[n]` Spring 설정으로 추가할 수 있습니다.

## 15. DB와 배포

사용 테이블:

```text
bamboo_messages
bamboo_nicknames
bamboo_reports
bamboo_moderation_audits
bamboo_settings
```

운영 DB에는 애플리케이션 실행 전에 [대나무숲 스키마](bamboo-schema.sql)를 적용합니다. 채팅에서 이모지를
정상 저장하려면 다섯 테이블 모두 `utf8mb4` 문자셋이어야 합니다.

기존 네 개 대나무숲 테이블을 사용하던 DB에는 `bamboo_moderation_audits`가 추가로 필요합니다. 기본
`spring.jpa.hibernate.ddl-auto=update` 환경은 재시작 시 테이블을 자동 생성합니다. 운영에서 DDL을
수동 관리하면 스키마 문서의 감사 이력 테이블 생성 구문만 먼저 실행합니다. 감사 기록은 60일 요청 로그
정리 대상이 아니며 자동 삭제되지 않습니다. 차단 사유에는 이름·학번·연락처 같은 불필요한 개인정보를
입력하지 않습니다.

현재 변경 커서 발급기는 단일 Festa 애플리케이션 인스턴스를 전제로 합니다. 동일 DB에 연결된 서버를
두 대 이상 동시에 실행하기 전에는 DB 시퀀스 또는 Redis 기반 전역 커서 발급기로 교체해야 합니다.

### 메시지 상태 변경 배포

DB의 기존 `HIDDEN`은 관리자 숨김 기록이므로 사용자 응답에서 `BLOCKED`로 변환하며 원문을 노출하지 않습니다. 신고 누적 `HIDDEN`은 DB `VISIBLE`과 `reportCount >= 5`에서 계산합니다. 기존 5회 이상 신고 글도 조회 시 적용됩니다. 신규 신고와 관리자 상태 변경은 같은 커서·트랜잭션 순서로 처리합니다.

배포 시 프런트의 상태 분기를 먼저 준비하고 최초 목록과 커서를 다시 조회합니다. 이전의 `status !== VISIBLE` 전체 제거 로직을 그대로 쓰면 신고 가림 글도 사라집니다. DB를 수동 관리하고 status가 ENUM이면 새 `BLOCKED` 저장값을 허용하도록 [상태 컬럼 갱신 SQL](bamboo-blocked-status-migration.sql)을 적용합니다. 기존 VARCHAR(20)은 그대로 사용합니다.

## 16. 배포 전 확인사항

- [ ] `bamboo-schema.sql` 적용
- [ ] 다섯 테이블의 collation이 `utf8mb4`인지 확인
- [ ] Festa 애플리케이션이 단일 인스턴스로 실행되는지 확인
- [ ] 작성자의 SSO 이메일 조회·SMTP 설정 및 사용자 경고 메일 수신 확인
- [ ] SMTP 계정에서 `no-reply@syu-likelion.org` 발송 권한 확인
- [ ] Nginx에 `/api/bamboo` 요청 크기 제한 설정
- [ ] 프런트가 `id` 기준으로 메시지를 갱신하는지 확인
- [ ] 최초 과거 조회 cursor와 이후 실시간 cursor를 구분하는지 확인
- [ ] `HIDDEN`은 가림·펼치기, `BLOCKED`·`DELETED`는 원문 제거로 처리하는지 확인
- [ ] `Retry-After`와 토큰 갱신 헤더를 처리하는지 확인
- [ ] 닉네임·본문을 HTML이 아닌 텍스트로 렌더링하는지 확인
- [ ] STAFF·ADMIN·SUPER_ADMIN 권한별 관리자 화면 확인
- [ ] STAFF가 차단·감사 이력 UI와 직접 처리 URL에 접근하지 못하는지 확인
- [ ] ADMIN 차단·해제 시 사유와 처리자 정보가 `bamboo_moderation_audits`에 저장되는지 확인
- [ ] 차단 사유에 불필요한 개인정보를 입력하지 않도록 운영자에게 안내
- [ ] 실제 MySQL에서 동시 작성·숨김·복구·닉네임 변경 테스트
- [ ] 실제 SMTP 신고 알림 발송 테스트


## SUPER_ADMIN 실제 사용자 검색·차단 (관리자 웹 전용)

오픈채팅 관리의 참여자 관리 영역에서 **실제 사용자 검색·차단**을 선택합니다.

| 메서드 | 경로 | 용도 |
|---|---|---|
| GET | `/admin/bamboo/users` | 검색 화면 |
| POST | `/admin/bamboo/users/search` | `query`, `page`로 사용자 검색 |
| POST | `/admin/bamboo/users/{userUuid}/mute` | `minutes`, `reason`으로 차단·해제 |

모든 경로는 관리자 쿠키 인증과 `SUPER_ADMIN` 권한이 필요합니다. POST에는 CSRF 토큰이 필요하며, ADMIN·STAFF는 접근할 수 없습니다. Bearer REST API가 아닌 HTML 화면용 경로입니다.

- 기존 사용자 검색과 동일하게 축제에 연결된 사용자 중 이름·학번·연락처·이메일·로그인 ID 등으로 검색합니다. 검색어는 공백 제외 2자 이상, 최대 100자이며 한 페이지에 20명을 표시합니다.
- 검색 결과에 이름·학번·학과·연락처·이메일·로그인 ID와 오픈채팅 닉네임·차단 상태·만료 시각을 표시합니다.
- 닉네임을 등록하지 않은 사용자는 미참여로 표시하며 차단할 수 없습니다. 아직 채팅을 쓰지 않았어도 닉네임을 등록했다면 차단할 수 있습니다.
- 선택한 사용자의 UUID로 조치하므로 검색 이후 닉네임이 변경돼도 같은 사용자에게 적용합니다.
- `minutes`는 1–525600, `0`이면 해제입니다. 사유는 1–200자 필수이며 기존 `bamboo_moderation_audits`에 처리자·대상·기간·사유를 기록합니다. 근거 메시지는 없으므로 `sourceMessageId=null`입니다.
- 조치 후 검색 결과를 다시 표시하려면 `query`, `page`를 함께 전달합니다.
- 신원 검색은 조회자 UUID·역할·결과 사용자 UUID·성공 여부를 로그에 남기며 검색어와 개인정보 값 자체는 기록하지 않습니다. 검색은 POST로 처리해 검색어를 URL에 넣지 않으며 응답은 `Cache-Control: no-store`입니다.
