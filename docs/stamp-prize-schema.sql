-- ddl-auto=update 환경은 엔티티로 자동 생성합니다. 수동 DDL 환경에서는 배포 전에 실행합니다.
CREATE TABLE IF NOT EXISTS festival_stamp_prizes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    target_user_uuid BINARY(16) NOT NULL,
    granted_by BINARY(16) NOT NULL,
    granted_at DATETIME(6) NOT NULL,
    stamp_count INT NOT NULL,
    issued BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_stamp_prize_target UNIQUE (target_user_uuid)
);

CREATE TABLE IF NOT EXISTS festival_stamp_prize_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    prize_id BIGINT NOT NULL,
    action VARCHAR(20) NOT NULL,
    method VARCHAR(20) NOT NULL,
    actor_uuid BINARY(16) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    stamp_count INT NOT NULL,
    reason VARCHAR(500),
    CONSTRAINT fk_stamp_prize_event FOREIGN KEY (prize_id) REFERENCES festival_stamp_prizes(id),
    INDEX idx_stamp_prize_event (prize_id, occurred_at, id)
);

-- 이미 festival_stamp_prizes가 있지만 issued/version 컬럼이 없는 수동 스키마 환경에서만 1회 실행:
-- ALTER TABLE festival_stamp_prizes
--     ADD COLUMN issued BOOLEAN NOT NULL DEFAULT TRUE,
--     ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
-- 과거 지급 건은 지급 완료로 유지합니다. 최초 철회 시 과거 지급도 이벤트로 보존됩니다.
