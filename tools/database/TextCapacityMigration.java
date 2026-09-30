import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.regex.*;

/** Java 21 source launcher; needs mysql-connector-j on the classpath. Dry-run by default. */
public class TextCapacityMigration {
    private static final Pattern EXPECTED = Pattern.compile(
            "SELECT '([^']+)' AS table_name, '([^']+)' AS column_name, '([^']+)' AS expected_type");
    private static final Pattern STRING_TYPE = Pattern.compile("(?i)(tinytext|text|mediumtext|longtext|varchar\\((\\d+)\\)|char\\((\\d+)\\))");

    public static void main(String[] args) throws Exception {
        boolean apply = Arrays.asList(args).contains("--apply");
        if (Arrays.stream(args).anyMatch(arg -> !arg.equals("--apply")))
            throw new IllegalArgumentException("Only --apply is supported; default is read-only.");
        Properties config = new Properties();
        Path configPath = Path.of("src/main/resources/env.properties");
        if (Files.exists(configPath)) try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            config.load(reader);
        }
        String url = setting(config, "DB_URL", null);
        if (url == null) {
            String app = Files.readString(Path.of("src/main/resources/application.properties"));
            Matcher matcher = Pattern.compile("(?m)^spring\\.datasource\\.url=\\$\\{DB_URL:(.*)}\\s*$").matcher(app);
            if (!matcher.find()) throw new IllegalStateException("Set DB_URL explicitly.");
            url = matcher.group(1);
        }
        Properties credentials = new Properties();
        credentials.setProperty("user", setting(config, "DB_USERNAME", ""));
        credentials.setProperty("password", setting(config, "DB_PASSWORD", ""));
        credentials.setProperty("connectTimeout", "10000");
        credentials.setProperty("socketTimeout", "120000");
        Map<String, String> expected = new TreeMap<>();
        Matcher matcher = EXPECTED.matcher(Files.readString(Path.of("docs/database-schema-compare.sql")));
        while (matcher.find()) expected.put(matcher.group(1) + "." + matcher.group(2), matcher.group(3));
        if (expected.isEmpty()) throw new IllegalStateException("Expected schema is missing; run DatabaseSchemaTests first.");
        try (Connection connection = DriverManager.getConnection(url, credentials)) {
            Path report = Path.of("build/reports/database/text-capacity",
                    java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")));
            migrate(connection, expected, apply, report);
        } catch (SQLException failure) {
            // JDBC exception messages may include connection details. Keep secrets out of console logs.
            System.err.println("Database operation failed: SQLState=" + failure.getSQLState()
                    + ", vendorCode=" + failure.getErrorCode() + ". Check connectivity/permissions and the saved plan.");
            System.exit(1);
        }
    }

    private static int migrate(Connection connection, Map<String, String> expected, boolean apply, Path report) throws Exception {
        if (connection.getCatalog() == null || connection.getCatalog().isBlank())
            throw new IllegalStateException("DB_URL must select the festival database.");
        Map<String, List<String>> changes = new TreeMap<>();
        Map<String, String> definitions = new TreeMap<>();
        List<String> targets = new ArrayList<>();
        Set<String> actual = new HashSet<>();
        try (Statement statement = connection.createStatement(); ResultSet columns = statement.executeQuery(
                "SELECT c.TABLE_NAME,c.COLUMN_NAME,c.DATA_TYPE,c.COLUMN_TYPE,c.CHARACTER_MAXIMUM_LENGTH "
                + "FROM information_schema.COLUMNS c JOIN information_schema.TABLES t "
                + "ON t.TABLE_SCHEMA=c.TABLE_SCHEMA AND t.TABLE_NAME=c.TABLE_NAME "
                + "WHERE c.TABLE_SCHEMA=DATABASE() AND t.TABLE_TYPE='BASE TABLE' ORDER BY c.TABLE_NAME,c.ORDINAL_POSITION")) {
            while (columns.next()) {
                String table = columns.getString(1), column = columns.getString(2), type = columns.getString(3);
                String key = table + "." + column;
                actual.add(key);
                String desired = expected.get(key);
                String target = targetType(type, columns.getLong(5), desired);
                if (target == null) continue;
                if (!definitions.containsKey(table)) try (Statement ddl = connection.createStatement();
                        ResultSet create = ddl.executeQuery("SHOW CREATE TABLE " + quote(table))) {
                    create.next();
                    definitions.put(table, create.getString(2));
                }
                String definition = columnDefinition(definitions.get(table), column, target);
                changes.computeIfAbsent(table, unused -> new ArrayList<>()).add("MODIFY COLUMN " + definition);
                targets.add(key + ": " + columns.getString(4) + " -> " + target);
            }
        }
        long missing = expected.keySet().stream().filter(key -> !actual.contains(key)).count();
        System.out.println("Mapped columns missing from DB: " + missing + " (requires deployment/schema creation separately).");
        Files.createDirectories(report);
        Files.writeString(report.resolve("before.sql"), String.join(";\n\n", definitions.values()) + ";\n");
        List<String> plan = new ArrayList<>();
        changes.forEach((table, edits) -> plan.add("ALTER TABLE " + quote(table) + "\n  " + String.join(",\n  ", edits) + ";"));
        Files.writeString(report.resolve("migration.sql"), String.join("\n\n", plan) + "\n");
        targets.forEach(System.out::println);
        System.out.println("Planned columns=" + targets.size() + ", tables=" + changes.size() + ", apply=" + apply);
        System.out.println("Schema snapshot and migration plan: " + report);
        if (!apply) return targets.size();
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET SESSION lock_wait_timeout=15");
            for (String sql : plan) {
                statement.execute(sql);
                System.out.println("Applied " + sql.substring(0, sql.indexOf('\n')));
            }
        }
        // Rerunning the same inspection must yield no remaining capacity changes.
        System.out.println("Post-migration verification:");
        if (migrate(connection, expected, false, report.resolve("verification")) != 0)
            throw new IllegalStateException("Some columns still need widening; inspect the verification plan.");
        return 0;
    }

    static String targetType(String actual, long length, String desired) {
        if (actual.equals("tinytext")) return "TEXT";
        if (desired == null || !STRING_TYPE.matcher(desired).matches()) return null;
        if (!Set.of("char", "varchar", "text", "mediumtext", "longtext").contains(actual)) return null;
        Matcher type = STRING_TYPE.matcher(desired);
        type.matches();
        if (desired.equalsIgnoreCase("text")) {
            // Do not shrink large VARCHARs or existing TEXT/MEDIUMTEXT/LONGTEXT.
            return Set.of("char", "varchar").contains(actual) && length <= 16383 ? "TEXT" : null;
        }
        if (type.group(2) == null && type.group(3) == null) return null;
        long required = Long.parseLong(type.group(2) != null ? type.group(2) : type.group(3));
        if (Set.of("char", "varchar").contains(actual)) return length < required ? "VARCHAR(" + required + ")" : null;
        return textBytes(actual) < required * 4 ? "MEDIUMTEXT" : null;
    }

    static String columnDefinition(String create, String column, String target) {
        String start = quote(column) + " ";
        for (String line : create.split("\\R")) {
            String definition = line.strip();
            if (!definition.startsWith(start)) continue;
            if (definition.endsWith(",")) definition = definition.substring(0, definition.length() - 1);
            Matcher type = STRING_TYPE.matcher(definition.substring(start.length()));
            if (!type.lookingAt()) throw new IllegalStateException("Unexpected column type: " + column);
            String tail = definition.substring(start.length() + type.end());
            // MySQL TEXT literal defaults require expression syntax. Preserve their value.
            if (target.endsWith("TEXT")) tail = tail.replaceFirst(
                    "(?i)^((?:\\s+(?:CHARACTER SET \\w+|COLLATE \\w+|NOT NULL|NULL))*)\\s+DEFAULT ('(?:[^'\\\\]|\\\\.|'')*')",
                    "$1 DEFAULT ($2)");
            return start + target + tail;
        }
        throw new IllegalStateException("Column definition not found: " + column);
    }

    private static long textBytes(String type) {
        return switch (type) { case "text" -> 65535L; case "mediumtext" -> 16777215L; default -> 4294967295L; };
    }
    private static String quote(String value) { return "`" + value.replace("`", "``") + "`"; }
    private static String setting(Properties config, String key, String fallback) {
        String value = System.getenv(key);
        return value != null ? value : config.getProperty(key, fallback);
    }
}
