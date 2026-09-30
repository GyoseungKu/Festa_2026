# 파일 업로드 공통 제한

[문서 목차](README.md) · [프런트 연동 시작하기](frontend-getting-started.md)

2026-09-14 저장소의 DTO, 저장소 구현, `application.properties` 기본값 기준입니다. 환경변수나 프록시 설정으로 더 작은 제한을 적용할 수 있습니다. 개수는 수정 후 남는 기존 파일과 새 파일을 합산합니다.

| 기능 | multipart 파트 | 허용 형식 | 개수 | 파일당 기본 제한 |
|---|---|---|---|---|
| 부스 이미지 | `files` 반복 | JPEG, PNG, WebP, GIF | 부스당 5개 | 10MB |
| 부스 동영상 | `files` 반복 | MP4, WebM, MOV | 부스당 3개 | 200MB |
| 공연 이미지 | `files` 반복 | JPEG, PNG, WebP, GIF | 링크와 업로드 합산 3개 | 10MB |
| 공연 동영상 | `files` 반복 | MP4, WebM, MOV | 링크와 업로드 합산 3개 | 200MB |
| 투표 선택지 | `file` | JPEG, PNG, WebP | 선택지당 1개, 새 파일로 교체 | 10MB |
| 투표 질문 | `files` 반복 | JPEG, PNG, WebP / MP4, WebM, MOV | 질문당 이미지·동영상 합산 3개 | 10MB / 200MB |
| 분실물 | `data` JSON + `images` 반복 | JPEG, PNG, WebP | 글당 5개 | 10MB |
| 협찬사 | `name`, `description`, `boothId`, `image` | JPEG, PNG, WebP | 1개 | 10MB |
| 일반 공지 | `data` JSON + `media`, `files` 반복 | [공지 허용 확장자](frontend-notices-api.md) | 두 그룹 합산 10개 | 이미지 10MB / 동영상 200MB / 문서 20MB |

부스·공연·투표·분실물·협찬사는 파일 파트의 MIME을 검사합니다. JPEG는 `image/jpeg`, MOV는 `video/quicktime`입니다. 일반 공지는 확장자 허용 목록으로 형식을 분류하고 MIME을 지정합니다. 허용 형식이 도메인마다 다르므로 하나의 공통 `accept` 목록을 모든 업로더에 사용하지 않습니다. 이 검증은 미디어 변환이나 동영상 코덱 호환성 검증을 뜻하지 않습니다.

## 파일 제한과 HTTP 요청 제한

- `r2.image-max-size` / `R2_IMAGE_MAX_SIZE`: 기본 10MB.
- `r2.video-max-size` / `R2_VIDEO_MAX_SIZE`: 기본 200MB.
- `notice.document-max-size` / `NOTICE_DOCUMENT_MAX_SIZE`: 기본 20MB.
- Servlet multipart 파일 하나 제한은 기본 200MB, 전체 요청은 기본 650MB입니다. 정확한 설정 키는 [application.properties](../src/main/resources/application.properties)를 확인합니다.
- 관리자 투표 폼은 파일 외에도 질문·선택지·체크박스·숨김 필드를 각각 multipart 파트로 전송합니다. Tomcat의 기본 50파트 제한으로 작은 폼도 413이 발생할 수 있어, `server.tomcat.max-part-count`와 `max-parameter-count`를 12,000으로 지정합니다. 텍스트 폼 합계는 16MB, 파트 헤더는 4KB까지 허용합니다. 기존 질문 50개·질문당 선택지 30개 제한과 파일당/전체 요청 제한은 유지합니다.
- 위 설정의 환경변수는 `SERVER_TOMCAT_MAX_PART_COUNT`, `SERVER_TOMCAT_MAX_PARAMETER_COUNT`, `SERVER_TOMCAT_MAX_HTTP_FORM_POST_SIZE`, `SERVER_TOMCAT_MAX_PART_HEADER_SIZE`입니다. 적용하려면 백엔드를 재시작해야 합니다.
- Nginx를 사용하는 배포에서는 백엔드로 전달하는 location의 `client_max_body_size 650m;`도 맞춰야 합니다. Spring 설정을 높여도 프록시의 작은 제한은 별도로 413을 반환합니다.
- MB 표기는 Spring `DataSize`의 1024 단위입니다. 예를 들어 10MB는 `10 * 1024 * 1024` 바이트입니다.
- 파일별 제한을 통과해도 요청 합계와 multipart 헤더·boundary가 전체 제한을 넘으면 실패합니다. 동영상 200MB 네 개를 한 요청으로 올릴 수 있다는 뜻이 아닙니다.
- 인증·권한 검사 → multipart 파싱 → DTO/도메인 검증 순서에 따라 먼저 발견된 오류가 반환됩니다. 미인증은 401, 업로드 권한 부족은 `403 MULTIPART_MANAGE_FORBIDDEN`, 잘못된 파트는 `400 INVALID_MULTIPART_REQUEST`, Servlet 크기 초과는 `413 UPLOAD_TOO_LARGE`가 될 수 있습니다. 개별 저장소의 크기·형식 위반은 주로 도메인 코드가 포함된 400입니다.

## FormData 구성과 실패 처리

`data` 파트는 `new Blob([JSON.stringify(payload)], { type: "application/json" })`으로 넣습니다. 일반 JSON 문자열 파트로만 보내면 해당 파트의 Content-Type이 맞지 않아 415가 발생할 수 있습니다. 요청 전체의 `Content-Type`은 브라우저가 boundary와 함께 설정하게 둡니다. 협찬사는 `data` 파트 대신 개별 폼 필드를 사용합니다.

분실물 수정의 `removeImageIds`, 공지 수정의 `removeAttachmentIds`는 같은 이름의 폼 필드를 반복 전송합니다. 다른 게시물의 ID를 보내지 않습니다. 부스·공연·투표 미디어 삭제는 해당 REST 경로로 별도 호출합니다.

R2 업로드 실패는 도메인별 503으로 처리됩니다. 응답이 끊긴 경우 서버가 이미 저장했을 수 있으므로 자동 재전송 전에 상세를 조회합니다. DB와 R2는 하나의 분산 트랜잭션이 아닙니다. 롤백 시 새 파일, 커밋 후 이전 파일을 정리하지만 저장소 삭제 실패 시 파일이 남을 수 있습니다.

관련 문서: [부스](frontend-booths-api.md), [공연](frontend-performances-api.md), [투표](frontend-polls-api.md), [분실물](frontend-lost-items-api.md), [공지](frontend-notices-api.md), [협찬사](frontend-sponsors-api.md).
