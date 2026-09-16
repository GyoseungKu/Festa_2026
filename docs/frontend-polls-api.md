# 투표·응답 폼 API

[문서 목차](README.md) · [프런트 연동 시작하기](frontend-getting-started.md)

**이 문서의 순서**

- [핵심 정책](#핵심-정책)
- [타입](#타입)
- [사용자 목록·상세](#사용자-목록상세)
- [응답 제출](#응답-제출)
- [내 제출 내역](#내-제출-내역)
- [사용자 결과](#사용자-결과)
- [ADMIN 이상 투표 생성·수정](#admin-이상-투표-생성수정)
- [선택지 이미지](#선택지-이미지)
- [질문 이미지·동영상](#질문-이미지동영상)
- [주요 오류](#주요-오류)

모든 투표 API는 로그인과 Bearer 인증이 필요하며, 일반 사용자 API는 학생 인증도 필수입니다. 공통 헤더, 토큰 갱신과 오류 처리는 [공통 API 규약](frontend-api-common.md)을 따릅니다.

## 학생 인증 필수

일반 사용자 API는 조회·참여 모두 로그인과 학생 인증이 필요합니다. `schoolVerificationStatus=VERIFIED`이고 인증 시각이 있어야 하며, 미인증·인증 회수 상태는 아래 오류를 반환합니다. 학생회비 납부 여부는 이용 조건이 아닙니다.

```http
HTTP/1.1 403 Forbidden
Content-Type: application/json

{"code":"SCHOOL_VERIFICATION_REQUIRED","message":"학생 인증 완료 후 이용할 수 있습니다."}
```

프런트는 `/api/users/me`의 `schoolVerified`로 진입 화면을 구성하고, 위 오류를 받으면 [학생 인증 흐름](frontend-school-sso.md)으로 안내합니다. 비로그인 요청은 `401`입니다. 일반 사용자 API에는 관리자 역할도 학생 인증 예외가 없으며, 별도 관리자 운영 API·페이지는 기존 역할 권한을 따릅니다. 경로와 성공 응답 구조는 동일하며, 이전에 허용되던 미인증 요청은 이제 `403`으로 거절됩니다.

## 핵심 정책

- 사용자 목록에는 공개일시가 지난 `진행 중`, `진행 예정`, `종료` 투표가 모두 표시됩니다.
- 목록 순서는 `OPEN → UPCOMING → ENDED`입니다.
- `allowMultipleSubmissions=false`이면 사용자당 1회, `true`이면 횟수 제한 없이 별도 응답을 제출합니다.
- 익명 투표도 서버에는 `userUuid`를 보관합니다. `ADMIN`에게는 숨기고 `SUPER_ADMIN`에게만 원본 신원을 제공합니다.
- `resultPublishedAt`이 null이면 결과는 관리자 전용입니다. 값이 있으면 해당 시각부터 사용자도 결과를 조회합니다.
- 결과 공개 시각이 종료 전이면 진행 중 집계도 실시간 공개됩니다.
- 관리자가 임의 종료한 투표는 다시 시작할 수 없습니다.

## 타입

```ts
type PollState = "UPCOMING" | "OPEN" | "ENDED";
type PollQuestionType =
  | "SINGLE_CHOICE"
  | "MULTIPLE_CHOICE"
  | "SHORT_TEXT"
  | "LONG_TEXT";

type PollOption = {
  id: number;
  text: string;
  imageUrl: string | null;
};

type PollQuestionMedia = {
  id: number;
  kind: "IMAGE" | "VIDEO";
  url: string;
  originalFilename: string;
  displayOrder: number;
};

type PollQuestion = {
  id: number;
  text: string;
  type: PollQuestionType;
  required: boolean;
  options: PollOption[];
  media: PollQuestionMedia[]; // displayOrder 오름차순
};

type PollSummary = {
  id: number;
  title: string;
  description: string;
  anonymous: boolean;
  allowMultipleSubmissions: boolean;
  publishedAt: string;
  startsAt: string;
  endsAt: string;
  resultPublishedAt: string | null;
  closedAt: string | null;
  state: PollState;
  hasSubmitted: boolean;
  mySubmissionCount: number;
  resultAvailable: boolean;
};

type PollDetail = PollSummary & {
  questions: PollQuestion[];
  createdAt: string;
  updatedAt: string;
};
```

모든 일시는 UTC ISO-8601 `Instant`입니다. 화면에서 `Asia/Seoul`로 변환합니다.

## 사용자 목록·상세

```http
GET /api/polls
GET /api/polls/{pollId}
Authorization: Bearer ACCESS_TOKEN
```

목록은 `PollSummary[]`, 상세는 `PollDetail`입니다. 공개 전 투표는 상세에서도 `404 POLL_NOT_FOUND`로 처리합니다.

화면 동작 권장안:

- `UPCOMING`: 시작 시각 표시, 제출 버튼 비활성화
- `OPEN`: 제출 가능
- `ENDED`: 제출 불가
- 1회 참여 투표에서 `hasSubmitted=true`: 다시 제출 버튼 비활성화
- 복수 참여 투표: `mySubmissionCount`를 참여 횟수로 표시하고 계속 제출 허용
- `resultAvailable=true`: 결과 보기 버튼 표시

## 응답 제출

```http
POST /api/polls/{pollId}/submissions
Content-Type: application/json
Authorization: Bearer ACCESS_TOKEN
```

```json
{
  "answers": [
    { "questionId": 11, "optionIds": [101], "text": null },
    { "questionId": 12, "optionIds": [201, 203], "text": null },
    { "questionId": 13, "optionIds": [], "text": "축제가 기대됩니다." }
  ]
}
```

질문 유형별 규칙:

| 유형 | `optionIds` | `text` |
|---|---|---|
| `SINGLE_CHOICE` | 0–1개, 필수 질문이면 정확히 1개 | null |
| `MULTIPLE_CHOICE` | 중복 없는 여러 개, 필수 질문이면 1개 이상 | null |
| `SHORT_TEXT` | 빈 배열 또는 생략 | 최대 500자 |
| `LONG_TEXT` | 빈 배열 또는 생략 | 최대 5,000자 |

선택 질문을 건너뛸 때는 해당 질문을 `answers`에서 생략해도 됩니다. 성공은 `201`입니다.

```json
{
  "submissionId": 501,
  "submittedAt": "2026-08-18T07:00:00Z",
  "mySubmissionCount": 2
}
```

## 내 제출 내역

```http
GET /api/polls/{pollId}/submissions/me
```

최신 제출순 배열입니다. 익명 투표여도 사용자는 자신의 제출 내용을 조회할 수 있습니다.

```ts
type MyPollSubmission = {
  id: number;
  submittedAt: string;
  answers: Array<{
    questionId: number;
    optionIds: number[];
    text: string | null;
  }>;
};
```

## 사용자 결과

```http
GET /api/polls/{pollId}/results
```

`resultPublishedAt`이 지나기 전에는 `403 POLL_RESULT_NOT_AVAILABLE`입니다.

```ts
type PollResult = {
  pollId: number;
  title: string;
  submissionCount: number;
  generatedAt: string;
  questions: Array<{
    questionId: number;
    text: string;
    type: PollQuestionType;
    answeredCount: number;
    options: Array<{ optionId: number; text: string; imageUrl: string | null; count: number; percentage: number }>;
    textAnswers: Array<{ text: string; submittedAt: string }>;
  }>;
};
```

복수 선택 질문의 퍼센트는 `선택 횟수 / 해당 질문 응답 수`이므로 합계가 100%를 넘을 수 있습니다. 공개 주관식 결과에는 작성자 신원이 포함되지 않습니다.

결과 선택지의 키는 상세의 `id`와 달리 **`optionId`**입니다. 질문별 `answeredCount`는 실제 답한 제출 수이며 건너뛴 질문은 제외됩니다. 전체 `submissionCount`는 응답 제출 수이므로 참여 인원과 다를 수 있습니다.

## ADMIN 이상 투표 생성·수정

```ts
type PollMutation = {
  title: string;                    // 최대 200자
  description: string;              // 최대 5,000자
  anonymous: boolean;
  allowMultipleSubmissions: boolean;
  publishedAt: string;
  startsAt: string;
  endsAt: string;
  resultPublishedAt: string | null; // null이면 관리자 전용
  questions: Array<{
    id?: number;                    // 수정 시 기존 ID
    text: string;
    type: PollQuestionType;
    required: boolean;
    options: Array<{ id?: number; text: string }>;
  }>;
};
```

| Method | Path | 설명 |
|---|---|---|
| `GET` | `/api/admin/polls` | 전체 투표 목록 |
| `GET` | `/api/admin/polls/{id}?page=0&size=50` | 실시간 집계와 제출 내역, size 최대 100 |
| `POST` | `/api/admin/polls` | 투표 생성, `201` |
| `PUT` | `/api/admin/polls/{id}` | 응답 전 질문·선택지 포함 전체 수정 |
| `PATCH` | `/api/admin/polls/{id}/settings` | 제목·설명·종료·결과 공개 시각 수정 |
| `POST` | `/api/admin/polls/{id}/close` | 되돌릴 수 없는 즉시 종료 |
| `POST` | `/api/admin/polls/{id}/questions/{questionId}/media` | 질문 이미지·동영상 업로드 |
| `PATCH` | `/api/admin/polls/{id}/questions/{questionId}/media/order` | 질문 미디어 통합 순서 변경 |
| `DELETE` | `/api/admin/polls/{id}/questions/{questionId}/media/{mediaId}` | 질문 미디어 삭제 |
| `DELETE` | `/api/admin/polls/{id}` | 응답 없는 투표 삭제 |
| `DELETE` | `/api/admin/polls/{id}?force=true` | `SUPER_ADMIN`의 응답 포함 강제 삭제 |

응답이 하나라도 생긴 뒤 `PUT` 또는 질문 미디어 변경 API를 호출하면 `409 POLL_STRUCTURE_LOCKED`입니다. 제목·설명·종료·결과 공개 시각은 settings API로 수정합니다. 선택지 이미지 변경과 즉시 종료는 별도 API로 계속 사용할 수 있습니다.

익명 투표 관리자 상세의 제출자 필드:

| 조회 권한 | `userUuid`, `userName`, `studentNo`, `department` |
|---|---|
| `ADMIN` | 모두 null |
| `SUPER_ADMIN` | 원본 사용자 정보 |

기명 투표는 `ADMIN` 이상에게 사용자 정보를 표시합니다.

### 필수값·수정 의미와 관리자 상세 구조

- 질문은 1–50개, 질문 문구는 1–500자입니다. 객관식 선택지는 2–30개, 문구는 1–200자이며 같은 질문의 trim 후 동일 문구는 거부됩니다. 주관식에는 선택지를 넣지 않습니다.
- `description`은 필수이지만 빈 문자열은 가능합니다. `endsAt`은 `startsAt`보다 늦어야 합니다. 공개 시각과 결과 공개 시각은 별도 정책이므로 화면에서 의도한 순서를 확인합니다.
- `answers`는 필수 배열(최대 50개)입니다. 같은 `questionId`를 중복 제출하거나 다른 투표의 질문·선택지를 넣으면 400입니다. 모든 질문이 선택이면 빈 배열로도 제출할 수 있습니다.
- settings PATCH는 `title`, `description`, `endsAt`을 모두 요구합니다. `resultPublishedAt`을 생략하거나 null로 보내면 결과를 관리자 전용으로 변경합니다. 기존 공개 시각을 유지하려면 다시 보내야 합니다.
- 관리자 상세의 페이지 메타데이터는 `submissions`에 적용됩니다. `results`는 투표 전체 집계이며 현재 페이지 집계가 아닙니다.
- 일반 사용자에게 제출 수정·삭제 API는 없습니다. 복수 참여 허용 시 POST는 항상 새 제출이므로 통신 실패 후 내 제출 내역을 확인하고 재전송합니다.

```ts
type PollAdminDetail = {
  poll: PollDetail;
  submissionCount: number;
  results: PollResult;
  submissions: Array<{
    id: number;
    userUuid: string | null;
    userName: string | null;
    studentNo: string | null;
    department: string | null;
    submittedAt: string;
    answers: Array<{
      questionId: number;
      questionText: string;
      optionIds: number[];
      optionTexts: string[];
      text: string | null;
    }>;
  }>;
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};
```

## 선택지 이미지

투표를 먼저 생성한 뒤 응답의 `option.id`로 업로드합니다.

```http
POST /api/admin/polls/{pollId}/options/{optionId}/image
Content-Type: multipart/form-data

file: IMAGE_FILE
```

```http
DELETE /api/admin/polls/{pollId}/options/{optionId}/image
```

선택지당 이미지 1개이며 JPG, PNG, WebP, 최대 10MB입니다. 새 이미지를 업로드하면 기존 파일을 교체합니다. FormData의 key는 `file`입니다.

현재 선택지 이미지 업로드·삭제는 응답이 생긴 뒤에도 가능합니다. 질문·선택지 구조를 바꾸는 `PUT`과 질문 미디어 업로드·정렬·삭제에 적용되는 `POLL_STRUCTURE_LOCKED` 제한과 구분합니다.

## 질문 이미지·동영상

투표를 먼저 생성한 뒤 상세 응답의 `question.id`로 업로드합니다. 한 질문에는 이미지와 동영상을 합해 최대 3개를 둘 수 있습니다.

```http
POST /api/admin/polls/{pollId}/questions/{questionId}/media
Content-Type: multipart/form-data

files: IMAGE_OR_VIDEO_FILE
files: IMAGE_OR_VIDEO_FILE
```

FormData key는 `files`이며 여러 파일을 같은 key로 보냅니다. 이미지는 JPG/PNG/WebP와 파일당 10MB, 동영상은 MP4/WebM/MOV와 파일당 200MB까지 허용합니다.

```http
PATCH /api/admin/polls/{pollId}/questions/{questionId}/media/order
Content-Type: application/json

{ "mediaIds": [31, 29, 30] }
```

현재 질문에 남아 있는 모든 미디어 ID를 중복 없이 원하는 순서대로 전송합니다. 응답의 `displayOrder`는 0부터 다시 매겨집니다.

```http
DELETE /api/admin/polls/{pollId}/questions/{questionId}/media/{mediaId}
```

질문 미디어는 투표 상세의 각 질문에 `media` 배열로 포함되므로 사용자 화면에서는 배열 순서대로 이미지 또는 `<video>`를 렌더링하면 됩니다. 응답이 한 건이라도 생긴 뒤에는 업로드·정렬·삭제가 모두 잠깁니다.

## 주요 오류

| HTTP | code | 처리 |
|---|---|---|
| `400` | `INVALID_POLL` | 질문 유형, 필수 답변, 선택지 소속, 일정 검증 |
| `400` | `UNSUPPORTED_POLL_OPTION_IMAGE_TYPE` | JPG/PNG/WebP 안내 |
| `400` | `POLL_OPTION_IMAGE_TOO_LARGE` | 10MB 이하 안내 |
| `400` | `POLL_QUESTION_MEDIA_LIMIT_EXCEEDED` | 질문별 이미지·동영상 합계 3개 이하로 조정 |
| `400` | `UNSUPPORTED_POLL_QUESTION_MEDIA_TYPE` | 허용 이미지·동영상 형식 안내 |
| `400` | `POLL_QUESTION_MEDIA_TOO_LARGE` | 이미지 10MB, 동영상 200MB 이하 안내 |
| `403` | `POLL_RESULT_NOT_AVAILABLE` | 결과 버튼 숨김 |
| `403` | `POLL_MANAGE_FORBIDDEN` | 관리자 UI 접근 차단 |
| `404` | `POLL_NOT_FOUND` | 목록으로 이동 |
| `404` | `POLL_OPTION_NOT_FOUND` | 상세 재조회 |
| `404` | `POLL_QUESTION_MEDIA_NOT_FOUND` | 상세 재조회 |
| `409` | `POLL_NOT_OPEN` | 상태 재조회 후 제출 버튼 비활성화 |
| `409` | `POLL_ALREADY_SUBMITTED` | 1회 투표 완료 상태 표시 |
| `409` | `POLL_STRUCTURE_LOCKED` | 설정 전용 수정 UI로 전환 |
| `409` | `POLL_ALREADY_ENDED` | 종료 상태 재조회 |
| `409` | `POLL_DELETE_LOCKED` | 응답이 있어 일반 삭제 불가 |

관리자 HTML 화면은 `/admin/polls`입니다.
