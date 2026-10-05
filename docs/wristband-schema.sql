-- 축제 무대 입장 팔찌. 기존 student_fee_lock (id=1)과 festival_users 테이블을 함께 사용합니다.
-- 학교 식별 해시는 재가입 중복 지급 방지를 위해 보존합니다. UUID에는 사용자 FK를 두지 않습니다.
CREATE TABLE IF NOT EXISTS festival_wristbands (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version BIGINT NOT NULL DEFAULT 0,
    subject_hash VARCHAR(64) NOT NULL,
    issued BOOLEAN NOT NULL,
    active_user_uuid CHAR(36) NULL,
    target_user_uuid CHAR(36) NULL,
    target_name VARCHAR(200) NOT NULL,
    target_student_no VARCHAR(10) NULL,
    target_department VARCHAR(100) NULL,
    issued_by CHAR(36) NOT NULL,
    issuer_name VARCHAR(200) NOT NULL,
    issued_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_wristband_subject (subject_hash),
    UNIQUE KEY uk_wristband_active_user (active_user_uuid),
    KEY idx_wristband_updated (updated_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS festival_wristband_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    wristband_id BIGINT NOT NULL,
    action ENUM('ISSUE','REVOKE','UPDATE_PROFILE') NOT NULL,
    target_user_uuid CHAR(36) NULL,
    actor_uuid CHAR(36) NOT NULL,
    actor_name VARCHAR(200) NOT NULL,
    reason VARCHAR(500) NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_wristband_events_record (wristband_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
