package io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Acceptability 持久化映射。
 *
 * <p>ISO/IEC 11179-3:2023 表 20（8.7.1.2）规定的可接受性取值为小写：
 * preferred / admitted / deprecated / superseded / obsolete。Java 枚举沿用大写常量
 * （Java 命名习惯），本转换器在落库时转为标准小写值，读取时按大小写不敏感解析回枚举，
 * 以兼容标准格式（小写）的历史导入数据。
 */
@Converter(autoApply = true)
public class AcceptabilityConverter implements AttributeConverter<Acceptability, String> {

    @Override
    public String convertToDatabaseColumn(Acceptability attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public Acceptability convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        return Acceptability.valueOf(dbData.trim().toUpperCase());
    }
}
