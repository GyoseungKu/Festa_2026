-- ddl-auto=update 환경은 엔티티로 자동 생성합니다. 수동 DDL 환경에서는 배포 전에 실행합니다.
CREATE TABLE IF NOT EXISTS festival_stamp_prizes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    target_user_uuid BINARY(16) NOT NULL,
    granted_by BINARY(16) NOT NULL,
    granted_at DATETIME(6) NOT NULL,
    stamp_count INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_stamp_prize_target UNIQUE (target_user_uuid)
);
