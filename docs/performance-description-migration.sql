-- Select the festival database and inspect the existing column before applying.
SHOW FULL COLUMNS FROM festival_performances LIKE 'description';

-- Widen TINYTEXT or a short VARCHAR to support the existing 5,000-character limit.
-- Skip if already TEXT/MEDIUMTEXT/LONGTEXT; do not narrow a larger existing column.
-- Preserve the existing utf8mb4 character set/collation if configured per column.
ALTER TABLE festival_performances MODIFY COLUMN description TEXT NOT NULL;

SHOW FULL COLUMNS FROM festival_performances LIKE 'description';
