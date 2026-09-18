-- Read-only MySQL 8 audit. Select the festival database before running.
SELECT DATABASE() AS selected_database, VERSION() AS mysql_version,
       @@sql_mode AS sql_mode, @@character_set_database AS database_charset,
       @@collation_database AS database_collation;

-- Any TINYTEXT/TINYBLOB needs review: capacity is measured in bytes.
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLLATION_NAME,
       CHARACTER_MAXIMUM_LENGTH, CHARACTER_OCTET_LENGTH
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND DATA_TYPE IN ('tinytext', 'tinyblob')
ORDER BY TABLE_NAME, ORDINAL_POSITION;

-- Table engine, charset, approximate size/row counts (not exact COUNT(*)).
SELECT TABLE_NAME, ENGINE, TABLE_COLLATION, TABLE_ROWS, DATA_LENGTH, INDEX_LENGTH
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'
ORDER BY DATA_LENGTH + INDEX_LENGTH DESC;

-- Verify primary/unique/query indexes actually exist in the deployed DB.
SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE,
       GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
GROUP BY TABLE_NAME, INDEX_NAME, NON_UNIQUE
ORDER BY TABLE_NAME, INDEX_NAME;

SELECT k.TABLE_NAME, k.CONSTRAINT_NAME, k.COLUMN_NAME,
       k.REFERENCED_TABLE_NAME, k.REFERENCED_COLUMN_NAME, r.DELETE_RULE, r.UPDATE_RULE
FROM information_schema.KEY_COLUMN_USAGE k
JOIN information_schema.REFERENTIAL_CONSTRAINTS r
  ON r.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA AND r.TABLE_NAME = k.TABLE_NAME
 AND r.CONSTRAINT_NAME = k.CONSTRAINT_NAME
WHERE k.CONSTRAINT_SCHEMA = DATABASE() AND k.REFERENCED_TABLE_NAME IS NOT NULL
ORDER BY k.TABLE_NAME, k.CONSTRAINT_NAME, k.ORDINAL_POSITION;

-- Generate, DO NOT execute, widening statements only for the six affected columns.
-- Retains each column's existing charset/collation and nullability.
-- Already-large TEXT/MEDIUMTEXT/LONGTEXT columns are left alone.
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE,
       CONCAT('ALTER TABLE `', TABLE_NAME, '` MODIFY COLUMN `', COLUMN_NAME,
              '` TEXT CHARACTER SET ', CHARACTER_SET_NAME, ' COLLATE ', COLLATION_NAME,
              IF(IS_NULLABLE = 'YES', ' NULL', ' NOT NULL'), ';') AS proposed_sql
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND (TABLE_NAME, COLUMN_NAME) IN (
    ('festival_performances', 'description'), ('festival_booths', 'description'),
    ('notices', 'content'), ('lost_item_notices', 'content'),
    ('festival_polls', 'description'), ('festival_poll_answers', 'text_value'))
  AND (DATA_TYPE = 'tinytext' OR (DATA_TYPE IN ('varchar', 'char') AND CHARACTER_MAXIMUM_LENGTH < 5000))
ORDER BY TABLE_NAME;
