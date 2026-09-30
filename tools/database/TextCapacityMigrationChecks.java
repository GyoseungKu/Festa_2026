/** Run with the JDK only; checks migration behavior without touching a database. */
public class TextCapacityMigrationChecks {
    public static void main(String[] args) {
        equal(TextCapacityMigration.targetType("tinytext", 255, null), "TEXT");
        equal(TextCapacityMigration.targetType("varchar", 255, "text"), "TEXT");
        equal(TextCapacityMigration.targetType("varchar", 65535, "text"), null);
        equal(TextCapacityMigration.targetType("mediumtext", 16777215, "text"), null);
        equal(TextCapacityMigration.targetType("varchar", 20, "varchar(150)"), "VARCHAR(150)");
        equal(TextCapacityMigration.targetType("varchar", 300, "varchar(150)"), null);
        equal(TextCapacityMigration.targetType("text", 65535, "varchar(2048)"), null);
        equal(TextCapacityMigration.targetType("text", 65535, "varchar(20000)"), "MEDIUMTEXT");
        String schema = "CREATE TABLE `test` (\n"
                + "  `body` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'it\\'s fine' COMMENT 'DEFAULT ''keep''',\n"
                + "  `optional` tinytext DEFAULT NULL COMMENT 'kept',\n"
                + "  `commented` tinytext NOT NULL COMMENT 'DEFAULT ''unchanged''',\n"
                + "  `name` varchar(20) NOT NULL DEFAULT 'name' INVISIBLE\n)";
        equal(TextCapacityMigration.columnDefinition(schema, "body", "TEXT"),
                "`body` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT ('it\\'s fine') COMMENT 'DEFAULT ''keep'''");
        equal(TextCapacityMigration.columnDefinition(schema, "optional", "TEXT"),
                "`optional` TEXT DEFAULT NULL COMMENT 'kept'");
        equal(TextCapacityMigration.columnDefinition(schema, "commented", "TEXT"),
                "`commented` TEXT NOT NULL COMMENT 'DEFAULT ''unchanged'''");
        equal(TextCapacityMigration.columnDefinition(schema, "name", "VARCHAR(150)"),
                "`name` VARCHAR(150) NOT NULL DEFAULT 'name' INVISIBLE");
        System.out.println("Migration checks passed (12 cases).");
    }
    private static void equal(Object actual, Object expected) {
        if (!java.util.Objects.equals(actual, expected))
            throw new AssertionError("Expected " + expected + ", got " + actual);
    }
}
