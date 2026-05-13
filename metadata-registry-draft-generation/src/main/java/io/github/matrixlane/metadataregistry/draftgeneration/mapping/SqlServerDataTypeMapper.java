package io.github.matrixlane.metadataregistry.draftgeneration.mapping;

import io.github.matrixlane.metadataregistry.draftgeneration.model.DataTypeMappingResult;
import io.github.matrixlane.metadataregistry.draftgeneration.model.MappingConfidence;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Set;

/**
 * SQL Server 数据类型到标准数据类型的映射器。
 *
 * <p>映射规则：
 * <ul>
 *   <li>VARCHAR/NVARCHAR → String（高置信度）</li>
 *   <li>INT → Integer（高置信度）</li>
 *   <li>BIGINT → Long（高置信度）</li>
 *   <li>DECIMAL/NUMERIC → Decimal（高置信度，但语义可能歧义）</li>
 *   <li>DATETIME/DATETIME2 → DateTime（高置信度）</li>
 *   <li>BIT → Boolean（高置信度）</li>
 *   <li>TEXT/NTEXT → Text（高置信度，LOB）</li>
 *   <li>BINARY/VARBINARY → Binary（高置信度）</li>
 *   <li>其他类型 → 未映射（低置信度，需人工确认）</li>
 * </ul>
 */
@Slf4j
public class SqlServerDataTypeMapper implements DataTypeMapper {

    /**
     * 高置信度直接映射表。
     */
    private static final Map<String, String> DIRECT_MAPPINGS = Map.ofEntries(
            Map.entry("VARCHAR", "String"),
            Map.entry("NVARCHAR", "String"),
            Map.entry("CHAR", "String"),
            Map.entry("NCHAR", "String"),
            Map.entry("INT", "Integer"),
            Map.entry("BIGINT", "Long"),
            Map.entry("SMALLINT", "Integer"),
            Map.entry("TINYINT", "Integer"),
            Map.entry("DECIMAL", "Decimal"),
            Map.entry("NUMERIC", "Decimal"),
            Map.entry("FLOAT", "Decimal"),
            Map.entry("REAL", "Decimal"),
            Map.entry("MONEY", "Decimal"),
            Map.entry("SMALLMONEY", "Decimal"),
            Map.entry("DATETIME", "DateTime"),
            Map.entry("DATETIME2", "DateTime"),
            Map.entry("SMALLDATETIME", "DateTime"),
            Map.entry("DATE", "Date"),
            Map.entry("TIME", "Time"),
            Map.entry("BIT", "Boolean"),
            Map.entry("TEXT", "Text"),
            Map.entry("NTEXT", "Text"),
            Map.entry("BINARY", "Binary"),
            Map.entry("VARBINARY", "Binary"),
            Map.entry("IMAGE", "Binary"),
            Map.entry("UNIQUEIDENTIFIER", "UUID"),
            Map.entry("XML", "XML")
    );

    /**
     * 中等置信度映射表（类型存在但可能有精度/语义差异）。
     */
    private static final Map<String, String> MEDIUM_MAPPINGS = Map.ofEntries(
            Map.entry("DATETIMEOFFSET", "DateTime"),
            Map.entry("TIMESTAMP", "Binary"),
            Map.entry("ROWVERSION", "Binary"),
            Map.entry("SQL_VARIANT", "String"),
            Map.entry("GEOMETRY", "String"),
            Map.entry("GEOGRAPHY", "String"),
            Map.entry("HIERARCHYID", "String")
    );

    /**
     * DECIMAL/NUMERIC 语义歧义类型（需要人工确认是金额、比例还是普通数值）。
     */
    private static final Set<String> SEMANTICALLY_AMBIGUOUS_TYPES = Set.of("DECIMAL", "NUMERIC", "MONEY", "SMALLMONEY");

    @Override
    public DataTypeMappingResult map(String sourceType, Integer columnSize, Integer decimalDigits) {
        if (sourceType == null || sourceType.isBlank()) {
            log.warn("Received null or blank source type, returning LOW confidence mapping");
            return DataTypeMappingResult.builder()
                    .sourceType(sourceType)
                    .mappedType("Unknown")
                    .confidence(MappingConfidence.LOW)
                    .reason("源数据类型为空或空白")
                    .build();
        }

        String normalizedType = sourceType.trim().toUpperCase();

        // 尝试直接映射（高置信度）
        if (DIRECT_MAPPINGS.containsKey(normalizedType)) {
            String mappedType = DIRECT_MAPPINGS.get(normalizedType);
            MappingConfidence confidence = MappingConfidence.HIGH;
            String reason = null;

            // DECIMAL/NUMERIC/MONEY 存在语义歧义，降级为中等置信度
            if (SEMANTICALLY_AMBIGUOUS_TYPES.contains(normalizedType)) {
                confidence = MappingConfidence.MEDIUM;
                reason = "DECIMAL/NUMERIC/MONEY 类型可能是金额、比例或普通数值，建议人工确认语义";
            }

            return DataTypeMappingResult.builder()
                    .sourceType(sourceType)
                    .mappedType(mappedType)
                    .confidence(confidence)
                    .reason(reason)
                    .build();
        }

        // 尝试中等置信度映射
        if (MEDIUM_MAPPINGS.containsKey(normalizedType)) {
            return DataTypeMappingResult.builder()
                    .sourceType(sourceType)
                    .mappedType(MEDIUM_MAPPINGS.get(normalizedType))
                    .confidence(MappingConfidence.MEDIUM)
                    .reason("类型已映射但可能存在精度或语义差异，建议人工确认")
                    .build();
        }

        // 未映射类型（低置信度）
        log.warn("Unmapped SQL Server data type: {}", sourceType);
        return DataTypeMappingResult.builder()
                .sourceType(sourceType)
                .mappedType("Unknown")
                .confidence(MappingConfidence.LOW)
                .reason("未识别的 SQL Server 数据类型: " + sourceType + "，需要人工确认")
                .build();
    }

    @Override
    public String getDatabaseType() {
        return "SQL_SERVER";
    }
}
