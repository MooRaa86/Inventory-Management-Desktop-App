package com.company.inventory.common.jpa;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalDate;

/** Stores LocalDate as ISO yyyy-MM-dd TEXT (lexicographic = chronological). */
@Converter(autoApply = true)
public class IsoLocalDateConverter implements AttributeConverter<LocalDate, String> {

    @Override
    public String convertToDatabaseColumn(LocalDate attribute) {
        return attribute == null ? null : attribute.toString();
    }

    @Override
    public LocalDate convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        return LocalDate.parse(dbData.trim());
    }
}
