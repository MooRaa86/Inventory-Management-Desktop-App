package com.company.inventory.common.jpa;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;

/**
 * Canonical storage format for all timestamps: fixed-width ISO-8601 TEXT
 * (yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS). Fixed width guarantees that lexicographic
 * ordering equals chronological ordering for SQL range filters.
 *
 * Reading is lenient and additionally accepts:
 * - plain ISO-8601 ("2026-08-23T14:30:05", variable fraction digits)
 * - SQLite datetime('now') output ("2026-08-23 14:30:05[.fff]")
 */
@Converter(autoApply = true)
public class IsoDateTimeConverter implements AttributeConverter<LocalDateTime, String> {

    public static final DateTimeFormatter CANONICAL =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS");

    private static final DateTimeFormatter LENIENT = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd[['T'][' ']HH:mm:ss]")
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
            .optionalEnd()
            .parseDefaulting(ChronoField.NANO_OF_SECOND, 0)
            .toFormatter();

    @Override
    public String convertToDatabaseColumn(LocalDateTime attribute) {
        return attribute == null ? null : CANONICAL.format(attribute);
    }

    @Override
    public LocalDateTime convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        String value = dbData.trim();
        try {
            return LocalDateTime.parse(value, CANONICAL);
        } catch (Exception ignored) {
            // fall through to lenient parsing
        }
        return LocalDateTime.parse(value, LENIENT);
    }
}
