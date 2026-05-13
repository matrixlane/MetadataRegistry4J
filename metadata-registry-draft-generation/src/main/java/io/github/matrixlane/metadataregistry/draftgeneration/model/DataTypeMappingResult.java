package io.github.matrixlane.metadataregistry.draftgeneration.model;

import lombok.Builder;
import lombok.Data;

/**
 * 数据类型映射结果。
 */
@Data
@Builder
public class DataTypeMappingResult {

    /**
     * 原始数据库类型（如 VARCHAR、INT）。
     */
    private String sourceType;

    /**
     * 映射后的标准数据类型（如 String、Integer）。
     */
    private String mappedType;

    /**
     * 映射置信度。
     */
    private MappingConfidence confidence;

    /**
     * 当置信度为 LOW 时，提供需要人工确认的原因。
     */
    private String reason;
}
