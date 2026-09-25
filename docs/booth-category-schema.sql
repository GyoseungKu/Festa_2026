-- 기존 festival_booths 테이블에 category 컬럼이 없는 환경에서 한 번 실행합니다.
-- ddl-auto=update로 이미 생성된 경우 실행하지 않습니다.
ALTER TABLE festival_booths
    ADD COLUMN category VARCHAR(32) NOT NULL DEFAULT 'GENERAL';

-- 기존 부스는 GENERAL로 초기화됩니다. 관리 화면에서 실제 분류로 변경하세요.
