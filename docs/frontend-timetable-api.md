# 타임테이블 API

개회식·공연·폐회식 등을 독립된 일정으로 등록합니다. 공연 일정에는 기존 공연팀을 선택적으로 연결할 수 있습니다.

## 인증과 경로

모든 API는 Bearer 인증이 필요합니다. 토큰 갱신 방식은 [공통 규약](frontend-api-common.md)을 따릅니다.

| 메서드 | 경로 | 권한 | 동작 |
|---|---|---|---|
| GET | `/api/timetable` | 로그인 | 모든 일정, 시작 시각·ID 오름차순 |
| GET | `/api/timetable/{id}` | 로그인 | 일정 하나 조회 |
| GET | `/api/timetable/admin` | ADMIN 이상 | 미공개 일정명과 공연팀 연결을 포함한 전체 조회 |
| POST | `/api/timetable` | ADMIN 이상 | 일정 등록, 201 |
| PATCH | `/api/timetable/{id}` | ADMIN 이상 | 일정 수정, 200 |
| DELETE | `/api/timetable/{id}` | ADMIN 이상 | 일정 삭제, 204 |

ADMIN 이상은 `ADMIN`, `SUPER_ADMIN`입니다. 일반 조회는 관리자 토큰으로 요청해도 공개 규칙을 적용합니다. 관리자 조회 및 등록·수정 응답에는 미공개 정보가 포함됩니다. 응답은 `Cache-Control: no-store`입니다.

## 등록·수정

```json
{
  "title": "천보 밴드의 무대",
  "startsAt": "2026-09-16T18:00:00+09:00",
  "endsAt": "2026-09-16T18:30:00+09:00",
  "publishedAt": "2026-09-16T12:00:00+09:00",
  "performanceId": 17
}
```

- `title`: 필수, 공백만 입력 불가, 최대 150자. 앞뒤 공백은 제거합니다.
- `startsAt`, `endsAt`, `publishedAt`: 필수, 시간대가 포함된 ISO 8601 시각. 종료는 시작보다 늦어야 합니다.
- `performanceId`: 선택, 존재하는 공연팀의 양수 ID. 개회식 등은 `null` 또는 생략합니다.
- PATCH도 필수 필드를 모두 전달합니다. `performanceId`를 `null`로 전달하거나 생략하면 기존 연결을 해제합니다.
- 공연팀 시간과 타임테이블 시간은 별도 값이며 자동 동기화하지 않습니다. 시간 중복은 허용합니다.
- 제목은 직접 입력한 값입니다. 연결된 공연팀 이름이 바뀌어도 제목은 유지됩니다.

## 공개 전 응답

목록은 다음 객체의 배열, 상세는 객체 하나입니다. 미공개 일정도 목록에 남습니다.

```json
{
  "id": 1,
  "title": "TBA",
  "startsAt": "2026-09-16T09:00:00Z",
  "endsAt": "2026-09-16T09:30:00Z",
  "publishedAt": "2026-09-16T03:00:00Z",
  "published": false,
  "performance": null
}
```

서버 현재 시각이 `publishedAt` 이상이면 `published=true`와 실제 `title`을 반환합니다. 별도 배치 작업 없이 다음 조회에 반영되므로 화면에서 다시 조회해야 합니다. `published`로 공개 상태를 판단하고 제목 문자열만으로 판단하지 않습니다.

일정과 공연팀이 모두 공개된 경우에만 일반 조회의 `performance`가 아래처럼 반환됩니다.

```json
{"id": 17, "teamName": "천보 밴드"}
```

일정 공개 시각과 [공연팀 공개 시각](frontend-performances-api.md)은 독립적입니다. 일정이 공개됐어도 팀이 미공개이면 `performance=null`입니다. **일정명에 팀 이름을 넣으면 일정 공개 시각에 그 이름이 공개되므로** 관리자가 두 공개 시각을 맞춰야 합니다. 반대로 공연팀 API의 공개 여부는 일정의 공개 시각에 영향을 받지 않습니다.

공연팀을 삭제하면 일정은 유지되고 연결만 해제됩니다. 일정 삭제는 공연팀을 삭제하지 않습니다.

## 오류

| 상태 | 코드 | 원인 |
|---|---|---|
| 403 | `TIMETABLE_MANAGE_FORBIDDEN` | ADMIN 미만의 관리 요청 |
| 404 | `SCHEDULE_NOT_FOUND` | 존재하지 않는 일정 |
| 404 | `PERFORMANCE_NOT_FOUND` | 연결할 공연팀이 없음 |
| 400 | `INVALID_SCHEDULE` | 서비스 검증에서 필수 값·길이·ID 오류 |
| 400 | `INVALID_SCHEDULE_TIME` | 종료 시각이 시작 이하 |

JSON 필드의 Bean Validation 오류 및 인증 오류 형식은 공통 규약을 따릅니다.

## 관리자 화면과 DB

- `/admin/timetable`: 전체 목록, 등록, 수정, 삭제. ADMIN 이상만 접근합니다.
- 입력 시각은 한국 시간(KST)이며 공개 전 일정명도 관리자에게 표시합니다.
- 새 테이블 `festival_schedules`는 기존 `ddl-auto=update` 정책으로 생성됩니다. 수동 생성용 SQL은 [timetable-schema.sql](timetable-schema.sql)입니다.
- API는 `Instant`를 사용하며 DB의 `DATETIME(6)`에는 기존 공연 기능과 동일하게 KST 값을 저장합니다.
