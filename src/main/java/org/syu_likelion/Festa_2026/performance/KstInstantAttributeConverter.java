package org.syu_likelion.Festa_2026.performance;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Keeps the domain/API type as {@link Instant} while storing a KST wall-clock
 * value in MySQL DATETIME columns.
 */
@Converter
public class KstInstantAttributeConverter implements AttributeConverter<Instant, LocalDateTime> {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Override
    public LocalDateTime convertToDatabaseColumn(Instant attribute) {
        return attribute == null ? null : LocalDateTime.ofInstant(attribute, SEOUL);
    }

    @Override
    public Instant convertToEntityAttribute(LocalDateTime dbData) {
        return dbData == null ? null : dbData.atZone(SEOUL).toInstant();
    }
}
