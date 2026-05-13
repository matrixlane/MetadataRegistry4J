package io.github.matrixlane.metadataregistry.draftgeneration.model;

import lombok.Builder;
import lombok.Data;

/**
 * 列级别的元数据草稿。
 */
@Data
@Builder
public class ColumnDraft {

    /**
     * 原始列名。
     */
    private String sourceColumnName;

    /**
     * 建议列名（经命名规范化处理）。
     */
    private String suggestedColumnName;

    /**
     * 原始数据类型。
     */
    private String sourceDataType;

    /**
     * 映射后的标准数据类型。
     */
    private String mappedDataType;

    /**
     * 映射置信度。
     */
    private MappingConfidence mappingConfidence;

    /**
     * 字段备注/描述。
     */
    private String remarks;

    /**
     * 列长度。
     */
    private Integer columnSize;

    /**
     * 小数位数。
     */
    private Integer decimalDigits;

    /**
     * 是否可为空。
     */
    private Boolean nullable;

    /**
     * 默认值。
     */
    private String defaultValue;

    /**
     * 是否自增。
     */
    private Boolean autoIncrement;

    /**
     * 命名建议。
     */
    private NamingSuggestion namingSuggestion;
}
