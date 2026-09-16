# 공개 약관 페이지

로그인 없이 열 수 있는 서버 렌더링 HTML 페이지입니다. 현재 내용은 페이지 구성을 위한 임시 문구이며 실제 약관이 아닙니다.

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

정식 문서를 받으면 각 본문 파일의 `content` 조각을 교체하고, [공통 레이아웃](../src/main/resources/templates/terms/layout.html)의 시행일·임시 작성본 안내를 갱신합니다. 두 문서의 시행일이 다르면 문서별 값으로 분리합니다. 임시 페이지의 검색 노출을 막는 `noindex, nofollow`도 정식 공개 시 검토합니다. 스타일은 [terms.css](../src/main/resources/static/css/terms.css)에서 관리하며 관리자 테마의 색상·글꼴을 사용합니다.
