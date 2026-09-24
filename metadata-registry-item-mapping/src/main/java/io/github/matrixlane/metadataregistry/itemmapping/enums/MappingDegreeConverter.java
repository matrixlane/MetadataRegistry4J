package io.github.matrixlane.metadataregistry.itemmapping.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * MappingDegree 持久化映射。
 *
 * <p>映射度取值规定为小写原文
 * （related to / same as / …）。Java 枚举沿用大写常量命名，本转换器落库时
 * 转为标准小写值，读取时按 {@link MappingDegree#fromStandardValue} 解析
 * （兼容历史大写存储）。参照 AcceptabilityConverter 先例。
 */
@Converter(autoApply = true)
public class MappingDegreeConverter implements AttributeConverter<MappingDegree, String> {

    @Override
    public String convertToDatabaseColumn(MappingDegree attribute) {
        return attribute == null ? null : attribute.standardValue();
    }

    @Override
    public MappingDegree convertToEntityAttribute(String dbData) {
        return MappingDegree.fromStandardValue(dbData);
    }
}
