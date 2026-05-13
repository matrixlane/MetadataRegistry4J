package io.github.matrixlane.metadataregistry.draftgeneration.mapping;

import io.github.matrixlane.metadataregistry.draftgeneration.model.DataTypeMappingResult;

/**
 * 数据库类型到标准数据类型的映射器接口。
 */
public interface DataTypeMapper {

    /**
     * 映射原始数据库类型到标准数据类型。
     *
     * @param sourceType    原始数据库类型名称
     * @param columnSize    列长度
     * @param decimalDigits 小数位数
     * @return 映射结果
     */
    DataTypeMappingResult map(String sourceType, Integer columnSize, Integer decimalDigits);

    /**
     * 返回此映射器支持的数据库类型。
     */
    String getDatabaseType();
}
