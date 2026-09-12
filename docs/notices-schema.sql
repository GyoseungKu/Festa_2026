-- 타임스탬프는 기존 KstInstantAttributeConverter에 맞춰 KST로 저장합니다.
CREATE TABLE IF NOT EXISTS notices (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(150) NOT NULL,
    content LONGTEXT NOT NULL,
    pinned BIT NOT NULL,
    banner BOOLEAN NOT NULL DEFAULT FALSE,
    pinned_at DATETIME(6),
    view_count BIGINT NOT NULL,
    author_uuid BINARY(16) NOT NULL,
    author_name VARCHAR(100) NOT NULL,
    last_modified_by_uuid BINARY(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    INDEX idx_notice_pin_created (pinned, pinned_at, created_at)
) DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS notice_attachments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    notice_id BIGINT NOT NULL,
    url VARCHAR(2048) NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(150) NOT NULL,
    size BIGINT NOT NULL,
    display_order INT,
    CONSTRAINT fk_notice_attachment_notice FOREIGN KEY (notice_id) REFERENCES notices(id)
) DEFAULT CHARSET=utf8mb4;
