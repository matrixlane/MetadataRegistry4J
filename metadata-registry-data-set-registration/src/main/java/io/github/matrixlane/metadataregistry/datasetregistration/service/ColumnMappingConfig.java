package io.github.matrixlane.metadataregistry.datasetregistration.service;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * Configurable column name mapping for interoperability dataset templates.
 *
 * <p>Maps column header names (Chinese or English) found in Excel/CSV files
 * to the internal field names used by {@code InteropDataElement}.
 *
 * <p>MVP: Provides default mappings based on common national health information
 * interoperability standard headers. Users can override or extend mappings.
 *
 * @see <a href="https://github.com/matrixlane/MetadataRegistry4j/">Dataset Registration</a>
 */
@Getter
@Setter
public class ColumnMappingConfig {

    /**
     * Default mappings from column header text to internal field name.
     *
     * <p>Supports both Chinese (互联互通标准) and English headers.
     */
    public static final Map<String, String> DEFAULT_MAPPINGS = new HashMap<>();

    static {
        // ── Chinese header mappings (互联互通标准数据集模板常用列名) ──
        DEFAULT_MAPPINGS.put("数据元标识符", "deIdentifier");
        DEFAULT_MAPPINGS.put("数据元标识", "deIdentifier");
        DEFAULT_MAPPINGS.put("DE标识符", "deIdentifier");
        DEFAULT_MAPPINGS.put("数据元名称", "deName");
        DEFAULT_MAPPINGS.put("数据元名", "deName");
        DEFAULT_MAPPINGS.put("定义", "deDefinition");
        DEFAULT_MAPPINGS.put("数据元定义", "deDefinition");
        DEFAULT_MAPPINGS.put("数据类型", "dataType");
        DEFAULT_MAPPINGS.put("表示格式", "representationFormat");
        DEFAULT_MAPPINGS.put("格式", "representationFormat");
        DEFAULT_MAPPINGS.put("允许值", "permissibleValues");
        DEFAULT_MAPPINGS.put("值域", "permissibleValues");
        DEFAULT_MAPPINGS.put("允许值/值域", "permissibleValues");
        DEFAULT_MAPPINGS.put("必填标识", "required");
        DEFAULT_MAPPINGS.put("是否必填", "required");
        DEFAULT_MAPPINGS.put("必填", "required");

        // ── English header mappings ──
        DEFAULT_MAPPINGS.put("Data Element Identifier", "deIdentifier");
        DEFAULT_MAPPINGS.put("DE Identifier", "deIdentifier");
        DEFAULT_MAPPINGS.put("Identifier", "deIdentifier");
        DEFAULT_MAPPINGS.put("Data Element Name", "deName");
        DEFAULT_MAPPINGS.put("DE Name", "deName");
        DEFAULT_MAPPINGS.put("Name", "deName");
        DEFAULT_MAPPINGS.put("Definition", "deDefinition");
        DEFAULT_MAPPINGS.put("Data Type", "dataType");
        DEFAULT_MAPPINGS.put("Representation Format", "representationFormat");
        DEFAULT_MAPPINGS.put("Format", "representationFormat");
        DEFAULT_MAPPINGS.put("Permissible Values", "permissibleValues");
        DEFAULT_MAPPINGS.put("Value Domain", "permissibleValues");
        DEFAULT_MAPPINGS.put("Required", "required");
    }

    /** The actual mapping override; falls back to DEFAULT_MAPPINGS for unmapped headers. */
    private final Map<String, String> customMappings = new HashMap<>();

    /**
     * Add or override a single column mapping.
     *
     * @param headerColumn the column header text as it appears in the file
     * @param internalField the internal field name (e.g. "deIdentifier", "deName")
     */
    public void addMapping(String headerColumn, String internalField) {
        customMappings.put(headerColumn, internalField);
    }

    /**
     * Resolve a column header to the internal field name.
     * Checks custom mappings first, then defaults, then returns the original header as fallback.
     */
    public String resolve(String headerColumn) {
        if (headerColumn == null) {
            return null;
        }
        String trimmed = headerColumn.trim();
        if (customMappings.containsKey(trimmed)) {
            return customMappings.get(trimmed);
        }
        if (DEFAULT_MAPPINGS.containsKey(trimmed)) {
            return DEFAULT_MAPPINGS.get(trimmed);
        }
        // Fallback: use the header as-is (allows flexible templates)
        return trimmed;
    }
}
