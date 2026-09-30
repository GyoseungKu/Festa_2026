# DB 스키마 점검 및 운영 DB 대조

2026-09-18 기준. 운영 DB에 직접 연결하지 않고 엔티티, 입력 검증, MySQLDialect 매핑을 점검했다. 운영에서 확인된 실제 타입은 `festival_performances.description = tinytext`뿐이다. 다른 테이블의 상태와 데이터 무결성·성능은 아래 운영 조회 결과가 필요하다.

## 수정한 저장 용량 문제

| 테이블.컬럼 | 입력 한도 | 수정한 매핑 |
|---|---|---|
| festival_performances.description | 5,000자 | TEXT |
| festival_booths.description | 5,000자 | TEXT |
| notices.content | 5,000자 | TEXT |
| lost_item_notices.content | 5,000자 | TEXT |
| festival_polls.description | 5,000자 | TEXT |
| festival_poll_answers.text_value | 단답형 500자, 장문형 5,000자 | TEXT, nullable 유지 |
| festival_poll_questions.question_text | 500자 | TEXT |
| festival_poll_options.option_text | 200자 | TEXT |
| festival_sponsors.description | 2,000자 | TEXT |
| bamboo_messages.content, birthday_messages.content | 400자 | TEXT |
| birthday_messages.public_department, public_masked_student_no, public_masked_name | 외부 프로필 표시값 | TEXT, 기존 nullable 유지 |
| bamboo_moderation_audits.reason | 200자 | TEXT |
| festival_wristband_events.reason, festival_stamp_prize_events.reason | 500자 | TEXT, nullable 유지 |

크기를 지정하지 않은 `@Lob String`에 의존하지 않고 컬럼 타입을 명시했다. TINYTEXT의 255바이트 제한은 한글 5,000자를 허용하는 입력 정책과 맞지 않는다. TEXT는 utf8mb4 기준 5,000자의 최대 20,000바이트를 수용한다. API 길이 제한과 필수값 규칙은 변경하지 않았다.

## 전체 컬럼 대조 방법

1. 운영 축제 DB를 선택한 상태에서 [운영 점검 SQL](database-operational-audit.sql)을 실행한다. 모두 조회문이며 회원 본문·개인정보는 출력하지 않는다.
2. 마지막 조회의 `proposed_sql`은 검토할 변경문 문자열이다. 자동 실행하지 않는다. 기존 문자셋·collation·NULL 허용을 유지하며 이미 충분히 큰 컬럼은 제외한다. 해당 컬럼에 별도 DEFAULT/COMMENT 등이 있다면 `SHOW CREATE TABLE`로 확인해 변경문에도 보존한다.
3. [전체 컬럼 비교 SQL](database-schema-compare.sql)을 실행한다. 모든 엔티티 및 컬렉션 테이블의 예상 타입·NULL 허용을 실제 DB와 비교한다. MISSING은 테이블/컬럼 누락, REVIEW는 타입 또는 NULL 차이다. 타입 별칭, 정수 표시 폭, 더 큰 컬럼도 차이로 표시될 수 있으므로 결과만 보고 축소 변경하면 안 된다.
4. 기본키·고유키·외래키·인덱스 조회 결과를 코드와 대조한다. 고유키를 추가하기 전에 중복 데이터 존재 여부를 별도로 확인한다. 대용량 테이블 변경의 잠금·소요시간은 운영 환경에서 판단해야 한다.

`DatabaseSchemaTests`는 실제 MySQLDialect로 전체 엔티티 매핑을 생성하여 TINYTEXT가 없는지, 위 17개 컬럼이 TEXT인지 검사한다. 비교 SQL은 테스트 실행 시 `build/reports/database/schema-compare.sql`에 재생성된다. `./gradlew exportDatabaseSchema`는 문서의 SQL까지 갱신한다. 이 테스트는 MySQL 서버 접속·실행 테스트가 아니다.

## 2026-09-30 전체 텍스트 용량 마이그레이션

현재 접속 설정은 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 환경변수를 우선 사용하며, 없으면 `src/main/resources/env.properties`와 `application.properties`의 기본값을 사용한다. 비밀번호를 명령행에 입력하거나 출력하지 않는다.

```powershell
# 조회만 수행하고 변경 SQL과 기존 테이블 정의를 저장
.\gradlew.bat databaseTextCapacity

# 선택한 DB의 모든 TINYTEXT를 TEXT로 확장하고, 전체 엔티티의 짧은 문자열 컬럼도 확장
.\gradlew.bat databaseTextCapacity -PapplyTextCapacity
```

[마이그레이션 도구](../tools/database/TextCapacityMigration.java)는 다음을 수행한다.

- 선택된 DB의 모든 실제 테이블에서 TINYTEXT를 찾아 TEXT로 변경한다.
- 위 TEXT 필드의 기존 짧은 CHAR/VARCHAR를 TEXT로 변경한다. 그 밖의 VARCHAR/CHAR도 엔티티가 요구하는 길이보다 짧으면 확장한다.
- 이미 충분히 큰 TEXT/MEDIUMTEXT/LONGTEXT나 큰 VARCHAR는 축소하지 않는다.
- `SHOW CREATE TABLE`의 컬럼 정의를 사용해 문자셋, collation, NULL, DEFAULT, COMMENT 및 추가 속성을 유지한다. TEXT의 문자열 기본값은 MySQL 표현식 형태로 보존한다.
- 테이블별로 ALTER를 묶어 실행한다. 변경 전 테이블 정의와 SQL은 `build/reports/database/text-capacity/<실행 시각>/`에 보존한다. 실행 후 다시 조회해 남은 용량 변경이 0개인지 검사한다.

테이블/컬럼이 누락된 경우에는 그 개수를 별도로 출력한다. 이 도구는 테이블 생성이나 다른 자료형 교정을 수행하지 않는다. MySQL DDL은 테이블별로 커밋되므로 중간 실패 시 원인을 해결한 후 재실행한다. 잠금 대기 제한은 15초다.

공연·부스·분실물·투표 이미지/미디어는 업로드 전에 원본 파일명을 최대 255자로 검증한다. 모든 R2 업로드 경로는 저장키의 DB 컬럼 길이와 1,024바이트 제한, URL의 2,048자 제한을 검증한다. 초과 파일명은 400 응답, 잘못된 저장 경로 설정은 명시적인 503 응답을 반환하며 파일 저장 전에 중단한다.

## 추가 확인 사항과 한계

- 채팅·생일 쪽지 본문 400자, 후원 설명 2,000자, 팔찌 철회 사유 500자는 명시적인 TEXT로 저장한다. 닉네임 15자(저장 20자) 등 짧은 문자열의 입력 제한은 유지한다. 운영 컬럼의 용량은 위 마이그레이션으로 대조한다.
- 회원 UUID/학생 식별 해시, 팔찌 중복 방지, 닉네임 중복, 신고·하트·스탬프 중복 방지에는 코드상 고유키가 정의돼 있다. 코드 정의만으로 운영 DB의 고유키 존재나 기존 데이터 정상 여부를 보장하지 않는다.
- 공지 첨부파일은 기존 파일명 검증을 유지하며 저장소 경로 검증도 함께 적용한다. 실제 DB가 코드보다 짧은 컬럼을 사용한다면 마이그레이션 적용이 필요하다.
- `ddl-auto=update`가 설정돼 있지만 기존 컬럼·제약조건이 항상 원하는 상태로 바뀐다고 가정하지 않는다. 운영 DB 변경 후 조회 결과로 확인한다.
- 디스크 여유, 백업 복원 가능 여부, DB 계정 권한, 실제 실행계획·느린 쿼리, 데이터 중복·고아 레코드, 로그 보존 작업은 로컬 엔티티 검사로 확인할 수 없다. 이번 결과는 운영 DB 전체가 정상이라는 판정이 아니다.
