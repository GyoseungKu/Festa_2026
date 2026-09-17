# 공개 약관 페이지

로그인 없이 열 수 있는 서버 렌더링 HTML 페이지입니다. 통합 SSO와 2026 천보축전 홈페이지를 함께 다루는 이용약관과 개인정보 처리방침을 게시합니다. 두 문서의 시행일은 **2026년 9월 23일**입니다.

| 문서 | 경로 | 본문 파일 |
|---|---|---|
| 이용약관 | `/terms/service` | [service.html](../src/main/resources/templates/terms/service.html) |
| 개인정보처리방침 | `/terms/privacy` | [privacy.html](../src/main/resources/templates/terms/privacy.html) |

프런트에서는 백엔드 주소를 기준으로 새 탭/창 링크를 제공합니다. 실제로 탭 또는 창 중 무엇이 열릴지는 브라우저 설정을 따릅니다.

```html
<a href="https://festa.syu-likelion.org/terms/service" target="_blank" rel="noopener noreferrer">이용약관 보기</a>
<a href="https://festa.syu-likelion.org/terms/privacy" target="_blank" rel="noopener noreferrer">개인정보처리방침 보기</a>
```

관리자 공통 하단에도 같은 링크를 연결했습니다. 사용자 프런트와 백엔드 도메인이 다르면 `/terms/...` 상대 경로 대신 백엔드의 절대 URL을 사용합니다. 페이지를 여는 데 API 토큰이나 별도 팝업 스크립트는 필요하지 않습니다.

본문은 각 파일의 `content` 조각, 문서 전체 제목은 각 파일의 `page(...)` 인자에서 관리합니다. 시행일은 [공통 레이아웃](../src/main/resources/templates/terms/layout.html)의 `time` 요소에 표시합니다. 향후 시행일이 달라지면 문서별 값으로 분리합니다. 현재 검색엔진 지시자는 `noindex, nofollow`입니다.

스타일은 [terms.css](../src/main/resources/static/css/terms.css)에서 관리하며 관리자 테마의 색상·글꼴을 사용합니다. 개인정보 처리방침의 넓은 표는 작은 화면에서 표 영역만 가로 스크롤되며, 키보드로도 접근할 수 있습니다. 인쇄 시에는 표가 용지 너비에 맞춰 표시됩니다.

이 페이지는 문서 열람용입니다. 문서 게시 자체가 회원가입 동의 수집·이력 저장, 연령 확인 또는 데이터 보유·파기 기능을 구현하는 것은 아닙니다. 해당 처리는 각 서비스의 가입·개인정보 관리 기능에서 별도로 적용합니다.
