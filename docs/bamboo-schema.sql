-- 대나무숲(익명 오픈채팅) 스키마
--
-- 이 파일은 배포 전에 수동으로 적용한다.
-- spring.jpa.hibernate.ddl-auto=update 로 생성하면 테이블 charset 이 DB 기본값을
-- 따라가는데, MariaDB 는 버전에 따라 기본값이 utf8mb4 가 아닐 수 있다.
-- 이모지 비중이 높은 채팅에서는 즉시 오류로 이어진다.
-- 수동 적용은 축제 당일 DDL 락 위험도 함께 제거한다.
--
-- ddl-auto=update 는 이미 존재하는 테이블을 변경하지 않으므로,
-- 애플리케이션 기동 전에 아래를 먼저 실행해 두면 그대로 사용된다.
--
-- 시각 컬럼은 KstInstantAttributeConverter 를 통해 KST 벽시계 값으로 저장된다.
-- 다른 테이블과 동일한 규칙이므로 DATETIME(6) 을 사용한다.


-- ---------------------------------------------------------------------------
-- 메시지
--
-- id  : 불변 식별자. 신고·삭제 대상 지정과 과거 조회 커서에 사용한다.
-- seq : 변경 스트림 커서. 작성 시점과 상태 변경 시점에 각각 새로 발급한다.
--       AUTO_INCREMENT 는 커밋 순서를 보장하지 않으므로 애플리케이션이 발급한다.
-- ---------------------------------------------------------------------------
CREATE TABLE bamboo_messages (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    seq           BIGINT       NOT NULL,
    user_uuid     BINARY(16)   NOT NULL,
    anon_name     VARCHAR(20)  NOT NULL,   -- 작성 시점 닉네임 스냅샷
    content       VARCHAR(400) NOT NULL,   -- 본문 제한은 200 코드포인트. UTF-16 기준 최대 400.
    status        VARCHAR(20)  NOT NULL,   -- VISIBLE | HIDDEN | DELETED
    report_count  INT          NOT NULL DEFAULT 0,
    created_at    DATETIME(6)  NOT NULL,
    hidden_at     DATETIME(6)  NULL,
    deleted_at    DATETIME(6)  NULL,
    deleted_by    BINARY(16)   NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bamboo_message_seq (seq),
    KEY idx_bamboo_message_user (user_uuid, created_at),
    KEY idx_bamboo_message_reports (report_count, created_at)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


-- ---------------------------------------------------------------------------
-- 닉네임
--
-- nickname     : 화면 표시용 원본
-- nickname_key : 중복 판정용 정규화 키
--                NFKC → 제로폭·제어문자 제거 → 공백 제거 → 소문자 → 호모글리프 매핑
-- muted_until  : 작성 차단 만료 시각. 닉네임이 없으면 작성 자체가 불가능하므로
--                작성 권한과 생명주기가 같다. 별도 테이블을 두지 않는다.
-- ---------------------------------------------------------------------------
CREATE TABLE bamboo_nicknames (
    user_uuid     BINARY(16)   NOT NULL,
    nickname      VARCHAR(20)  NOT NULL,
    nickname_key  VARCHAR(20)  NOT NULL,
    muted_until   DATETIME(6)  NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (user_uuid),
    UNIQUE KEY uk_bamboo_nickname_key (nickname_key)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


-- ---------------------------------------------------------------------------
-- 신고
--
-- (message_id, user_uuid) UNIQUE 로 중복 신고를 차단한다.
-- 이 유니크 인덱스가 message_id 선행 인덱스를 겸하므로 별도 인덱스를 두지 않는다.
-- 메시지와 JPA 연관을 맺지 않는다. 관리자 목록은 집계 조회이므로
-- 연관을 두면 조회 경로에 지연 로딩이 들어간다.
-- ---------------------------------------------------------------------------
CREATE TABLE bamboo_reports (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    message_id  BIGINT      NOT NULL,
    user_uuid   BINARY(16)  NOT NULL,
    reason      VARCHAR(20) NOT NULL,   -- ABUSE | SPAM | SEXUAL | PERSONAL_INFO | IMPERSONATION | OTHER
    created_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bamboo_report (message_id, user_uuid)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


-- ---------------------------------------------------------------------------
-- 설정
--
-- id = 1 단일 행으로 운용한다.
-- 킬스위치 상태는 재시작 후에도 유지되어야 하므로 DB 에 둔다.
-- ---------------------------------------------------------------------------
CREATE TABLE bamboo_settings (
    id          BIGINT      NOT NULL,
    enabled     BOOLEAN     NOT NULL DEFAULT TRUE,
    read_only   BOOLEAN     NOT NULL DEFAULT FALSE,
    closes_at   DATETIME(6) NULL,
    updated_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 시각 컬럼은 KST 벽시계 값으로 저장하므로 시드 행도 같은 기준으로 넣는다.
INSERT INTO bamboo_settings (id, enabled, read_only, closes_at, updated_at)
VALUES (1, TRUE, FALSE, NULL, UTC_TIMESTAMP(6) + INTERVAL 9 HOUR);


-- ---------------------------------------------------------------------------
-- 적용 확인
-- ---------------------------------------------------------------------------
-- SELECT TABLE_NAME, TABLE_COLLATION
--   FROM information_schema.TABLES
--  WHERE TABLE_SCHEMA = DATABASE()
--    AND TABLE_NAME LIKE 'bamboo\_%';
--
-- 네 테이블 모두 utf8mb4_unicode_ci 여야 한다.
