-- 수동 스키마 관리 환경에서, 애플리케이션을 중지한 뒤 적용한다.
-- Hibernate가 만든 기존 ENUM 컬럼도 BLOCKED를 저장할 수 있도록 VARCHAR로 통일한다.
-- HIDDEN 데이터는 변경하지 않는다. 새 코드는 기존 HIDDEN을 BLOCKED로 응답한다.
ALTER TABLE bamboo_messages MODIFY COLUMN status VARCHAR(20) NOT NULL;
