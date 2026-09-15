-- 기존 festival_performances 테이블 생성 후 실행합니다.
-- DATETIME 값은 KstInstantAttributeConverter에 맞춰 KST로 저장합니다.
CREATE TABLE IF NOT EXISTS festival_schedules (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(150) NOT NULL,
    starts_at DATETIME(6) NOT NULL,
    ends_at DATETIME(6) NOT NULL,
    published_at DATETIME(6) NOT NULL,
    performance_id BIGINT NULL,
    created_by BINARY(16) NOT NULL,
    updated_by BINARY(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    INDEX idx_schedule_starts_at (starts_at),
    INDEX idx_schedule_performance (performance_id),
    CONSTRAINT fk_schedule_performance FOREIGN KEY (performance_id)
        REFERENCES festival_performances(id) ON DELETE SET NULL
) DEFAULT CHARSET=utf8mb4;
