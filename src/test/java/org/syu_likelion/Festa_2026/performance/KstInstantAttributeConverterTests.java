package org.syu_likelion.Festa_2026.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class KstInstantAttributeConverterTests {
    private final KstInstantAttributeConverter converter = new KstInstantAttributeConverter();

    @Test
    void storesInstantAsKstLocalDateTime() {
        Instant instant = Instant.parse("2026-08-12T07:30:00Z");

        assertThat(converter.convertToDatabaseColumn(instant))
                .isEqualTo(LocalDateTime.of(2026, 8, 12, 16, 30));
    }

    @Test
    void restoresKstLocalDateTimeAsSameInstant() {
        LocalDateTime stored = LocalDateTime.of(2026, 8, 12, 16, 30);

        assertThat(converter.convertToEntityAttribute(stored))
                .isEqualTo(Instant.parse("2026-08-12T07:30:00Z"));
    }

    @Test
    void preservesNullValues() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
