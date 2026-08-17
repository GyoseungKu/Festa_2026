# 스탬프 API

스탬프판은 축제 기간 중 사용자당 하나만 존재하며 회차나 초기화 개념이 없습니다. 한 사용자는 각 부스에서 현재 스탬프를 최대 하나만 보유할 수 있습니다. 관리자가 정정 목적으로 회수한 뒤 다시 지급할 수 있지만, 모든 지급과 회수는 감사 이력에 남습니다.

## 사용자 스탬프판

- GET /api/users/me/stamps
- 로그인 필수

응답 예시:

    {
      "participated": true,
      "stampCount": 2,
      "stamps": [
        {
          "boothId": 1,
          "boothName": "체험 부스",
          "operator": "운영팀",
          "grantedAt": "2026-08-17T03:00:00Z"
        }
      ]
    }

participated는 스탬프를 한 번이라도 받은 적이 있으면 이후 모든 스탬프가 회수돼도 true입니다.

## QR 조회 및 지급·회수

BOOTH_MANAGER, ADMIN, SUPER_ADMIN이 사용할 수 있습니다. BOOTH_MANAGER는 자신에게 배정된 부스에서만 동작합니다.

- POST /api/booths/{boothId}/stamps/qr/lookup
- POST /api/booths/{boothId}/stamps/qr/grant
- POST /api/booths/{boothId}/stamps/qr/revoke

요청 본문:

    { "token": "사용자의 동적 QR 토큰" }

QR 조회 응답의 stamped가 현재 지급 상태입니다. 부스 관리자에게는 이름과 학번이 마스킹되며 사용자 UUID는 반환하지 않습니다.

## ADMIN 이상 임의 지급·회수

- POST /api/booths/{boothId}/stamps/users/{userUuid}/grant
- POST /api/booths/{boothId}/stamps/users/{userUuid}/revoke

ADMIN, SUPER_ADMIN만 사용할 수 있습니다. 관리자 웹 화면에서는 축제 서비스에 연결된 사용자를 이름, 아이디, 학번, 학과 또는 UUID로 검색할 수 있습니다.

## 부스별 현황 및 감사 이력

- GET /api/booths/{boothId}/stamps/history
- BOOTH_MANAGER: 자신에게 배정된 부스만 조회 가능
- ADMIN, SUPER_ADMIN: 모든 부스 조회 가능

현재 스탬프 보유자와 지급·회수 전체 이력을 반환합니다. 이력에는 대상 사용자, 처리 관리자, 처리 시각 및 QR/ADMIN_SEARCH 처리 방식이 포함됩니다.
BOOTH_MANAGER 응답에서는 사용자·처리자 UUID가 `null`이며 이름과 학번이 마스킹됩니다. ADMIN 이상은 원본 정보를 조회합니다.

관리자 웹 화면은 /admin/stamps 입니다. 부스 관리자는 담당 부스만 드롭다운에 표시되며 QR 처리만 사용할 수 있고, STAFF는 접근할 수 없습니다.
