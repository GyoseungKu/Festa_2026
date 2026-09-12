# 관리자 공통 디자인

- 공통 테마: `src/main/resources/static/css/admin-console.css`
- 사이드바: `templates/admin/fragments/sidebar.html`
- 기존 `admin.css`는 기능별 배치와 컴포넌트 규칙을 유지하며, 새 테마 파일을 뒤에서 로드합니다.
- 색상은 `.admin-page`의 `--admin-*` 변수를 수정합니다. 별도 웹폰트를 내려받지 않으며 한글 시스템 폰트를 대체 폰트로 사용합니다.

로그인 화면을 제외한 모든 관리자 화면은 `admin-page → admin-layout → sidebar + admin-workspace` 구조입니다. workspace에는 관리 본문·푸터가 들어가며 별도 상단 헤더는 표시하지 않습니다. 로그인 계정 이름, 관리 권한과 POST 로그아웃 폼은 공통 사이드바에 표시합니다. 로그아웃 폼은 Thymeleaf의 CSRF 토큰 처리를 유지합니다. 로그인은 테마만 적용하고 사이드바를 표시하지 않습니다.

사이드바 메뉴는 기존 관리 권한에 맞춰 표시합니다. 컨트롤러에서 `adminNavigationRoles`에 전체 권한을 전달하므로 STAFF+BOOTH_MANAGER 같은 복합 권한도 반영합니다. 활성 메뉴는 `adminCurrentPath`로 판별합니다. 메뉴를 숨기는 것과 별개로 기존 서버 권한 검사는 유지됩니다.

800px 이하에서는 사이드바를 계정 정보와 로그아웃이 포함된 상단 메뉴로 바꾸고, 560px 이하에서는 기능 카드를 1열로 표시합니다. 키보드 포커스와 모션 감소 설정도 지원합니다. 대시보드의 중복 계정 요약 카드는 표시하지 않으며, 사용 가능한 기능과 개수는 관리 기능 섹션에서 확인합니다.

새 관리자 화면도 공통 CSS 두 개, body 클래스, 사이드바 조각과 workspace를 포함해야 합니다. 사용자용 API와 인증 흐름은 디자인 변경 대상이 아닙니다.
