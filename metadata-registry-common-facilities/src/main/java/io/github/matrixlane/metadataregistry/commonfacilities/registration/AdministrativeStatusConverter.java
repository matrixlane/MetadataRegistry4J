package io.github.matrixlane.metadataregistry.commonfacilities.registration;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * AdministrativeStatus 持久化映射。
 *
 * <p>管理状态落库值为标准原文小写串（如 {@code provisionally qualified}），
 * 读取时按 {@link AdministrativeStatus#fromStandardValue} 解析（大小写不敏感、
 * trim 容忍、未知值抛错）。
 */
@Converter(autoApply = true)
public class AdministrativeStatusConverter implements AttributeConverter<AdministrativeStatus, String> {

    @Override
    public String convertToDatabaseColumn(AdministrativeStatus attribute) {
        return attribute == null ? null : attribute.standardValue();
    }

    @Override
    public AdministrativeStatus convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        return AdministrativeStatus.fromStandardValue(dbData);
    }
}
