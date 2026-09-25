-- design_no 컬럼이 없는 기존 DB에서 한 번 실행합니다.
-- ddl-auto=update로 이미 생성된 경우 실행하지 않습니다.
ALTER TABLE birthday_messages
    ADD COLUMN design_no INT NOT NULL DEFAULT 1;
