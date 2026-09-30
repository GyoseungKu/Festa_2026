package org.syu_likelion.Festa_2026.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Entity;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

class DatabaseSchemaTests {
    @Test
    void mysqlMappingsSupportLongTextAndExportReadOnlySchemaComparison() throws Exception {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .applySetting("hibernate.physical_naming_strategy",
                        "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl")
                .build();
        try {
            var scanner = new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
            var sources = new MetadataSources(registry);
            for (var bean : scanner.findCandidateComponents("org.syu_likelion.Festa_2026")) {
                sources.addAnnotatedClass(Class.forName(bean.getBeanClassName()));
            }
            var metadata = sources.buildMetadata();
            Set<String> longText = Set.of("festival_performances.description", "festival_booths.description",
                    "notices.content", "lost_item_notices.content", "festival_polls.description",
                    "festival_poll_answers.text_value", "festival_poll_questions.question_text",
                    "festival_poll_options.option_text", "festival_sponsors.description",
                    "bamboo_messages.content", "birthday_messages.content",
                    "birthday_messages.public_department", "birthday_messages.public_masked_student_no",
                    "birthday_messages.public_masked_name", "bamboo_moderation_audits.reason",
                    "festival_wristband_events.reason", "festival_stamp_prize_events.reason");
            List<String> checked = new ArrayList<>();
            List<String> rows = new ArrayList<>();
            for (var namespace : metadata.getDatabase().getNamespaces()) {
                for (var table : namespace.getTables()) {
                    for (var column : table.getColumns()) {
                        String type = column.getSqlType(metadata);
                        String key = table.getName() + "." + column.getName();
                        assertThat(type).as(key + " must not silently use TINYTEXT").doesNotStartWith("tinytext");
                        if (longText.contains(key)) {
                            assertThat(type).as(key).isEqualToIgnoringCase("TEXT");
                            checked.add(key);
                        }
                        rows.add("SELECT " + sql(table.getName()) + " AS table_name, "
                                + sql(column.getName()) + " AS column_name, " + sql(normalizeType(type))
                                + " AS expected_type, " + sql(column.isNullable() ? "YES" : "NO")
                                + " AS expected_nullable");
                    }
                }
            }
            assertThat(checked).containsExactlyInAnyOrderElementsOf(longText);
            rows.sort(String::compareTo);
            String query = "-- Generated from all Hibernate entities using MySQLDialect. Read-only.\n"
                    + "-- USE the festival database first. Differences require review, not automatic ALTER.\n"
                    + "-- Type aliases/display widths and larger existing columns may be harmless differences.\n"
                    + "WITH expected AS (\n" + String.join("\nUNION ALL\n", rows) + "\n)\n"
                    + "SELECT e.*, c.COLUMN_TYPE AS actual_type, c.IS_NULLABLE AS actual_nullable,\n"
                    + " c.COLLATION_NAME, c.CHARACTER_MAXIMUM_LENGTH, c.CHARACTER_OCTET_LENGTH,\n"
                    + " CASE WHEN c.COLUMN_NAME IS NULL THEN 'MISSING' ELSE 'REVIEW' END AS finding\n"
                    + "FROM expected e LEFT JOIN information_schema.COLUMNS c\n"
                    + " ON c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = e.table_name AND c.COLUMN_NAME = e.column_name\n"
                    + "WHERE c.COLUMN_NAME IS NULL OR LOWER(c.COLUMN_TYPE) <> e.expected_type\n"
                    + " OR c.IS_NULLABLE <> e.expected_nullable\nORDER BY e.table_name, e.column_name;\n";
            Path output = Path.of("build/reports/database/schema-compare.sql");
            Files.createDirectories(output.getParent());
            Files.writeString(output, query, StandardCharsets.UTF_8);
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    private static String sql(String value) { return "'" + value.replace("'", "''") + "'"; }

    private static String normalizeType(String type) {
        String normalized = type.toLowerCase(java.util.Locale.ROOT).split(" default ", 2)[0].trim();
        return normalized.equals("boolean") ? "tinyint(1)" : normalized;
    }
}
