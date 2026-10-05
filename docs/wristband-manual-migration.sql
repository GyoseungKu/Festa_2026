-- 기존 팔찌 테이블에 적용합니다. 신규 DB는 wristband-schema.sql을 사용합니다.
-- 배포 전에 적용하며, target_* 신규 컬럼이 이미 있으면 ADD COLUMN 두 항목은 생략합니다.
ALTER TABLE festival_wristbands
    MODIFY COLUMN target_user_uuid CHAR(36) NULL,
    ADD COLUMN target_student_no VARCHAR(10) NULL,
    ADD COLUMN target_department VARCHAR(100) NULL;

ALTER TABLE festival_wristband_events
    MODIFY COLUMN target_user_uuid CHAR(36) NULL,
    MODIFY COLUMN action ENUM('ISSUE','REVOKE','UPDATE_PROFILE') NOT NULL;
